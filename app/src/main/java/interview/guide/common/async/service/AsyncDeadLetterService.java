package interview.guide.common.async.service;

import interview.guide.common.async.DeadLetterMetrics;
import interview.guide.common.async.dto.DeadLetterResponse;
import interview.guide.common.async.model.AsyncDeadLetterEntity;
import interview.guide.common.async.model.DeadLetterStatus;
import interview.guide.common.async.repository.AsyncDeadLetterRepository;
import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.mapper.DeadLetterMapper;
import interview.guide.infrastructure.redis.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 异步任务死信服务
 * <p>
 * 职责：
 * 1. {@link #saveDeadLetter} 在消费重试耗尽后归档原始消息（D2 由 AbstractStreamConsumer 调用）；
 * 2. {@link #replay} 将死信原样重新投递回对应 Stream 并标记 REPLAYED（D4 管理页重投按钮）；
 * 3. {@link #discard} 标记 RESOLVED，人工关闭；
 * 4. {@link #listByStatus} 供管理页列表查询。
 * <p>
 * 死信 payload 列保存原始 Stream 消息体(JSON)，重投时直接反序列化回 Map 重新投递，
 * 并将 retryCount 重置为 0，避免重投的消息一进来就被判"重试耗尽"再次进死信。
 */
@Slf4j
@Service
public class AsyncDeadLetterService {

    private final AsyncDeadLetterRepository repository;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    private final DeadLetterMapper deadLetterMapper;
    private final DeadLetterMetrics deadLetterMetrics;

    @Autowired
    public AsyncDeadLetterService(
        AsyncDeadLetterRepository repository,
        RedisService redisService,
        ObjectMapper objectMapper,
        DeadLetterMapper deadLetterMapper,
        DeadLetterMetrics deadLetterMetrics
    ) {
        this.repository = repository;
        this.redisService = redisService;
        this.objectMapper = objectMapper;
        this.deadLetterMapper = deadLetterMapper;
        this.deadLetterMetrics = deadLetterMetrics;
    }

    /**
     * 消费失败超过重试上限后，将原始消息归档到死信表。
     *
     * @param streamKey           Stream 名称（如 interview:evaluate:stream）
     * @param groupName          消费者组名
     * @param messageId          原始 Stream 消息 ID（XAUTOCLAIM 回收前的 id，用于排查）
     * @param taskType           任务类型（默认取 streamKey，便于筛选）
     * @param payload            原始消息体 Map（会被序列化为 JSON 存入 payload 列）
     * @param payloadIdentifier  业务唯一标识（如 sessionId / kbId），重投幂等去重用
     * @param errorMessage       失败原因（会被截断到 500 字）
     * @param retryCount         消费端已重试次数
     */
    public void saveDeadLetter(
        String streamKey,
        String groupName,
        String messageId,
        String taskType,
        Map<String, String> payload,
        String payloadIdentifier,
        String errorMessage,
        int retryCount
    ) {
        AsyncDeadLetterEntity entity = new AsyncDeadLetterEntity();
        entity.setStreamKey(streamKey);
        entity.setGroupName(groupName);
        entity.setMessageId(truncate(messageId, 64));
        entity.setTaskType(taskType);
        entity.setPayload(serializePayload(payload));
        entity.setPayloadIdentifier(payloadIdentifier);
        entity.setErrorMessage(truncate(errorMessage, 500));
        entity.setStatus(DeadLetterStatus.DEAD);
        entity.setRetryCount(retryCount);
        entity.setReplayCount(0);
        // createdAt / updatedAt 由 @PrePersist / @PreUpdate 填充
        repository.save(entity);
        deadLetterMetrics.incrementDeadLetterArchived(taskType);
        log.info("任务已归档死信表: streamKey={}, payloadIdentifier={}, deadLetterId={}",
            streamKey, payloadIdentifier, entity.getId());
    }

    /**
     * 重投死信：把原始消息体重新投递回对应 Stream，并标记状态为 REPLAYED。
     */
    public AsyncDeadLetterEntity replay(Long id) {
        AsyncDeadLetterEntity entity = repository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.DEAD_LETTER_NOT_FOUND, "死信ID: " + id));
        if (entity.getStatus() == DeadLetterStatus.RESOLVED) {
            throw new BusinessException(ErrorCode.DEAD_LETTER_STATUS_INVALID, "已人工解决的死信不可重投");
        }

        Map<String, String> payloadMap = deserializePayload(entity.getPayload());
        // 重置重试计数，给重投任务一个完整的重试预算
        payloadMap.put(AsyncTaskStreamConstants.FIELD_RETRY_COUNT, "0");

        try {
            redisService.streamAdd(entity.getStreamKey(), payloadMap, AsyncTaskStreamConstants.STREAM_MAX_LEN);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.DEAD_LETTER_REPLAY_FAILED, "重投失败: " + e.getMessage(), e);
        }

        entity.setStatus(DeadLetterStatus.REPLAYED);
        entity.setReplayCount(entity.getReplayCount() + 1);
        entity.setLastReplayAt(LocalDateTime.now());
        AsyncDeadLetterEntity saved = repository.save(entity);
        deadLetterMetrics.incrementReplayed(saved.getTaskType());
        return saved;
    }

    /**
     * 人工解决：标记死信为 RESOLVED，不再重投。
     */
    public AsyncDeadLetterEntity discard(Long id) {
        AsyncDeadLetterEntity entity = repository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.DEAD_LETTER_NOT_FOUND, "死信ID: " + id));
        if (entity.getStatus() == DeadLetterStatus.RESOLVED) {
            throw new BusinessException(ErrorCode.DEAD_LETTER_STATUS_INVALID, "死信已处于 RESOLVED 状态，无法再次解决");
        }
        entity.setStatus(DeadLetterStatus.RESOLVED);
        AsyncDeadLetterEntity saved = repository.save(entity);
        deadLetterMetrics.incrementResolved(saved.getTaskType());
        return saved;
    }

    /**
     * 按状态列出死信；status 为 null 时返回全部。
     */
    public List<AsyncDeadLetterEntity> listByStatus(DeadLetterStatus status) {
        if (status == null) {
            return repository.findAll();
        }
        return repository.findByStatus(status);
    }

    /**
     * 按状态列出死信并转换为响应对象（VO），供管理页列表查询。
     * 已隔离 Entity 的 payload 大字段，符合「禁止直接返回 Entity」规范。
     */
    public List<DeadLetterResponse> listResponses(DeadLetterStatus status) {
        return deadLetterMapper.toResponseList(listByStatus(status));
    }

    private String serializePayload(Map<String, String> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("死信 payload 序列化失败，降级为 toString: {}", e.getMessage());
            return String.valueOf(payload);
        }
    }

    private Map<String, String> deserializePayload(String payload) {
        try {
            return objectMapper.readValue(payload, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.DEAD_LETTER_REPLAY_FAILED, "死信 payload 反序列化失败: " + e.getMessage(), e);
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
