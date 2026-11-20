package interview.guide.common.async.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 异步任务死信表实体
 * 对应 async_task_dead_letter 表（由 V20261008__add_async_task_dead_letter.sql 建表）。
 * 消费失败超过重试上限后由 AbstractStreamConsumer.markFailed 写入。
 */
@Entity
@Table(name = "async_task_dead_letter", indexes = {
    @Index(name = "idx_dead_letter_stream_key", columnList = "streamKey"),
    @Index(name = "idx_dead_letter_status", columnList = "status"),
    @Index(name = "idx_dead_letter_payload_identifier", columnList = "payloadIdentifier")
})
public class AsyncDeadLetterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Redis Stream 名称（如 evaluate、voiceEvaluate、vectorize）
    @Column(nullable = false, length = 100)
    private String streamKey;

    // 消费者组名称
    @Column(nullable = false, length = 100)
    private String groupName;

    // Stream 消息 ID（XAUTOCLAIM 回收前的原始 id，用于排查）
    @Column(length = 64)
    private String messageId;

    // 任务类型（如 RESUME_ANALYZE、INTERVIEW_EVALUATE）
    @Column(length = 50)
    private String taskType;

    // 原始消息体（重投时直接重新投递此内容）
    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    // 业务唯一标识（如 knowledge_base_id / session_id），用于重投幂等去重
    @Column(length = 200)
    private String payloadIdentifier;

    // 失败原因（截断存储，避免过长）
    @Column(length = 500)
    private String errorMessage;

    // 死信状态：DEAD / REPLAYED / RESOLVED
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeadLetterStatus status;

    // 消费端重试次数（达到上限才进死信）
    private Integer retryCount = 0;

    // 人工重投次数
    private Integer replayCount = 0;

    // 归档时间
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // 最近一次重投时间
    private LocalDateTime lastReplayAt;

    // 最后更新时间
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
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

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getPayloadIdentifier() {
        return payloadIdentifier;
    }

    public void setPayloadIdentifier(String payloadIdentifier) {
        this.payloadIdentifier = payloadIdentifier;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public DeadLetterStatus getStatus() {
        return status;
    }

    public void setStatus(DeadLetterStatus status) {
        this.status = status;
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
