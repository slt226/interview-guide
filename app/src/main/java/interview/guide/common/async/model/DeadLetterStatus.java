package interview.guide.common.async.model;

/**
 * 死信表状态枚举
 * 对应 async_task_dead_letter.status 列（VARCHAR，由实体字段 @Enumerated(EnumType.STRING) 持久化）。
 */
public enum DeadLetterStatus {
    DEAD,       // 已归档待重投（消费失败超过重试上限后写入）
    REPLAYED,   // 已重投（重新注入 Stream，原任务进入新一轮生命周期）
    RESOLVED    // 已人工解决（确认无需重投，标记关闭）
}
