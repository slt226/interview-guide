package interview.guide.common.async.dto;

import interview.guide.common.async.model.DeadLetterStatus;

import java.time.LocalDateTime;

/**
 * 死信列表响应对象（VO）。
 * <p>
 * 与 {@code AsyncDeadLetterEntity} 字段基本一致，但<b>刻意去掉 {@code payload} 大字段</b>：
 * payload 是原始 Stream 消息体（TEXT，可能很大），列表场景既不需要展示、也不应序列化给前端，
 * 避免浪费带宽且遵守「禁止直接返回 Entity」的规范。重投时由后端从 Entity 读取 payload，前端无感知。
 */
public class DeadLetterResponse {

    private Long id;

    // Redis Stream 名称（如 interview:evaluate:stream）
    private String streamKey;

    // 消费者组名称
    private String groupName;

    // Stream 消息 ID（XAUTOCLAIM 回收前的原始 id，用于排查）
    private String messageId;

    // 任务类型（如 RESUME_ANALYZE、INTERVIEW_EVALUATE）
    private String taskType;

    // 业务唯一标识（如 knowledge_base_id / session_id）
    private String payloadIdentifier;

    // 死信状态：DEAD / REPLAYED / RESOLVED
    private DeadLetterStatus status;

    // 失败原因
    private String errorMessage;

    // 消费端重试次数
    private Integer retryCount;

    // 人工重投次数
    private Integer replayCount;

    // 归档时间
    private LocalDateTime createdAt;

    // 最近一次重投时间
    private LocalDateTime lastReplayAt;

    // 最后更新时间
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStreamKey() {
        return streamKey;
    }

    public void setStreamKey(String streamKey) {
        this.streamKey = streamKey;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getPayloadIdentifier() {
        return payloadIdentifier;
    }

    public void setPayloadIdentifier(String payloadIdentifier) {
        this.payloadIdentifier = payloadIdentifier;
    }

    public DeadLetterStatus getStatus() {
        return status;
    }

    public void setStatus(DeadLetterStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getReplayCount() {
        return replayCount;
    }

    public void setReplayCount(Integer replayCount) {
        this.replayCount = replayCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastReplayAt() {
        return lastReplayAt;
    }

    public void setLastReplayAt(LocalDateTime lastReplayAt) {
        this.lastReplayAt = lastReplayAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
