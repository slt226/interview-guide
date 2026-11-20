package interview.guide.common.async;

import interview.guide.common.async.service.AsyncDeadLetterService;
import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.infrastructure.redis.RedisService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public abstract class AbstractStreamConsumer<T> {

    private final RedisService redisService;
    @Autowired
    private AsyncDeadLetterService deadLetterService;
    @Autowired
    private DeadLetterMetrics deadLetterMetrics;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executorService;
    private String consumerName;

    protected AbstractStreamConsumer(RedisService redisService) {
        this.redisService = redisService;
    }

    @PostConstruct
    public void init() {
        this.consumerName = consumerPrefix() + UUID.randomUUID().toString().substring(0, 8);
        this.executorService = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            r -> {
                Thread t = new Thread(r, threadName());
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.AbortPolicy()
        );

        running.set(true);
        executorService.submit(this::startConsumer);
        log.info("{} consumer started: consumerName={}", taskDisplayName(), consumerName);
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        if (executorService != null) {
            executorService.shutdown();
        }
        log.info("{} consumer stopped: consumerName={}", taskDisplayName(), consumerName);
    }

    private void startConsumer() {
        try {
            redisService.createStreamGroup(streamKey(), groupName());
            log.info("Redis Stream group is ready: {}", groupName());
        } catch (Exception e) {
            log.warn("Failed to prepare Redis Stream group: groupName={}", groupName(), e);
        }

        consumeLoop();
    }

    private void consumeLoop() {
        while (running.get()) {
            try {
                redisService.streamConsumeMessages(
                    streamKey(),
                    groupName(),
                    consumerName,
                    AsyncTaskStreamConstants.BATCH_SIZE,
                    AsyncTaskStreamConstants.POLL_INTERVAL_MS,
                    AsyncTaskStreamConstants.PENDING_IDLE_TIMEOUT_MS,
                    AsyncTaskStreamConstants.PENDING_CLAIM_BATCH_SIZE,
                    this::processMessage
                );
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    log.info("Consumer thread interrupted");
                    break;
                }
                log.error("Failed to consume message", e);
            }
        }
    }

    private void processMessage(StreamMessageId messageId, Map<String, String> data) {
        T payload;
        try {
            payload = parsePayload(messageId, data);
        } catch (Exception e) {
            Object fields = data == null ? null : data.keySet();
            log.warn("Failed to parse {} stream message, ack and discard: messageId={}, fields={}",
                taskDisplayName(), messageId, fields, e);
            ackMessage(messageId);
            return;
        }

        if (payload == null) {
            ackMessage(messageId);
            return;
        }

        int retryCount = parseRetryCount(data);
        log.info("Processing {} task: payload={}, messageId={}, retryCount={}",
            taskDisplayName(), payloadIdentifier(payload), messageId, retryCount);

        // 消费幂等：Redis SET NX 占位。Redis Stream 是 at-least-once 语义，且 XAUTOCLAIM 会在
        // pending 5min 后把消息回收重投，重复投递是必然事件；同一任务被执行两次 = 重复调大模型 = 重复计费。
        // 这里在业务处理前占位，重复投递 trySet 返回 false 直接 ACK 跳过，从入口挡掉重复执行。
        String idemKey = buildIdempotencyKey(payload);
        if (!redisService.trySetIfAbsent(idemKey, payloadIdentifier(payload), AsyncTaskStreamConstants.IDEMPOTENCY_TTL)) {
            deadLetterMetrics.incrementIdempotencyHit(taskType());
            log.warn("{} 幂等拦截：检测到重复投递，已跳过 -> key={}", taskDisplayName(), idemKey);
            ackMessage(messageId);
            return;
        }

        try {
            if (shouldSkip(payload)) {
                ackMessage(messageId);
                log.info("{} task skipped: {}", taskDisplayName(), payloadIdentifier(payload));
                return;
            }
            if (!tryMarkProcessing(payload)) {
                ackMessage(messageId);
                log.info("{} task was not claimed: {}", taskDisplayName(), payloadIdentifier(payload));
                return;
            }
            processBusiness(payload);
            markCompleted(payload);
            ackMessage(messageId);
            log.info("{} task completed: {}", taskDisplayName(), payloadIdentifier(payload));
        } catch (Exception e) {
            // 释放幂等键：失败的任务后续会通过 retryMessage 重新入队、或由死信表手动重投再次被处理，
            // 若不删除该键，它会被一直挡住，导致重试/重投永远无法执行（任务卡死）。
            redisService.delete(idemKey);
            log.error("{} task failed: {}", taskDisplayName(), payloadIdentifier(payload), e);
            if (retryCount < AsyncTaskStreamConstants.MAX_RETRY_COUNT) {
                retryMessage(payload, retryCount + 1);
            } else {
                persistDeadLetter(payload, messageId, data, retryCount, e);
            }
            ackMessage(messageId);
        }
    }

    /**
     * 幂等键：streamKey + payloadIdentifier 组合，保证同一业务任务在处理窗口内只被处理一次。
     * payloadIdentifier 由各子类实现（如 sessionId / kbId / resumeId），子类零改动即可获得幂等能力。
     */
    protected String buildIdempotencyKey(T payload) {
        return AsyncTaskStreamConstants.IDEMPOTENCY_KEY_PREFIX + streamKey() + ":" + payloadIdentifier(payload);
    }

    /**
     * 任务类型，用于死信表 task_type 列。默认取 streamKey，子类可覆写为更具可读性的分类名。
     */
    protected String taskType() {
        return streamKey();
    }

    /**
     * 消费重试耗尽后：标记业务状态为 FAILED，并将原始消息归档到死信表，供管理页手动重投。
     */
    private void persistDeadLetter(T payload, StreamMessageId messageId, Map<String, String> data,
                                   int retryCount, Exception e) {
        String error = truncateError(taskDisplayName() + " 重试 " + retryCount + " 次后仍失败: " + e.getMessage());
        try {
            markFailed(payload, error);
            deadLetterService.saveDeadLetter(
                streamKey(),
                groupName(),
                messageId == null ? null : messageId.toString(),
                taskType(),
                data,
                payloadIdentifier(payload),
                error,
                retryCount
            );
        } catch (Exception dlqEx) {
            // 死信表写入失败不能影响主流程：至少业务状态已是 FAILED，记录日志便于人工排查。
            log.error("写入死信表失败，任务仅标记为 FAILED: payload={}", payloadIdentifier(payload), dlqEx);
        }
    }

    protected int parseRetryCount(Map<String, String> data) {
        if (data == null) {
            return 0;
        }
        try {
            return Integer.parseInt(data.getOrDefault(AsyncTaskStreamConstants.FIELD_RETRY_COUNT, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    protected String truncateError(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 500 ? error.substring(0, 500) : error;
    }

    private void ackMessage(StreamMessageId messageId) {
        try {
            redisService.streamAck(streamKey(), groupName(), messageId);
        } catch (Exception e) {
            log.error("Failed to ack stream message: messageId={}", messageId, e);
        }
    }

    protected RedisService redisService() {
        return redisService;
    }

    protected abstract String taskDisplayName();

    protected abstract String streamKey();

    protected abstract String groupName();

    protected abstract String consumerPrefix();

    protected abstract String threadName();

    protected abstract T parsePayload(StreamMessageId messageId, Map<String, String> data);

    protected abstract String payloadIdentifier(T payload);

    protected boolean shouldSkip(T payload) {
        return false;
    }

    protected abstract void markProcessing(T payload);

    /**
     * 尝试领取任务。默认保持原有消费者的状态更新语义。
     */
    protected boolean tryMarkProcessing(T payload) {
        markProcessing(payload);
        return true;
    }

    protected abstract void processBusiness(T payload);

    protected abstract void markCompleted(T payload);

    protected abstract void markFailed(T payload, String error);

    protected abstract void retryMessage(T payload, int retryCount);
}
