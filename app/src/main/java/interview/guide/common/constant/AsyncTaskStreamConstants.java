package interview.guide.common.constant;

import java.time.Duration;

/**
 * 异步任务 Redis Stream 通用常量
 * 包含知识库向量化和简历分析两个异步任务的配置
 */
public final class AsyncTaskStreamConstants {

    private AsyncTaskStreamConstants() {
        // 私有构造函数，防止实例化
    }

    // ========== 通用消息字段 ==========

    /**
     * 重试次数字段
     */
    public static final String FIELD_RETRY_COUNT = "retryCount";

    /**
     * 文档内容字段
     */
    public static final String FIELD_CONTENT = "content";

    // ========== 通用消费者配置 ==========

    /**
     * 最大重试次数
     */
    public static final int MAX_RETRY_COUNT = 3;

    /**
     * 每次拉取的消息批次大小
     */
    public static final int BATCH_SIZE = 10;

    /**
     * Pending 消息空闲超过该时间后允许被当前消费者认领（毫秒）
     */
    public static final long PENDING_IDLE_TIMEOUT_MS = 5 * 60 * 1000;

    /**
     * 每轮最多回收的 pending 消息数
     */
    public static final int PENDING_CLAIM_BATCH_SIZE = 10;

    /**
     * 消费者轮询间隔（毫秒）
     */
    public static final long POLL_INTERVAL_MS = 1000;

    /**
     * Stream 最大长度（自动裁剪旧消息，防止无限增长）
     */
    public static final int STREAM_MAX_LEN = 1000;

    // ========== 死信与消费幂等相关 ==========

    /**
     * 幂等键前缀：消费入口用 Redis SET NX 占位，防止 Redis Stream at-least-once
     * + XAUTOCLAIM（pending 5min 回收）导致的重复投递被重复处理/重复计费。
     */
    // ========== 死信与消费幂等相关 ==========
    public static final String IDEMPOTENCY_KEY_PREFIX = "async:dedup:";
    public static final Duration IDEMPOTENCY_TTL = Duration.ofMinutes(30);


    // ========== 知识库向量化 Stream 配置 ==========

    public static final String KB_VECTORIZE_STREAM_KEY = "knowledgebase:vectorize:stream";
    public static final String KB_VECTORIZE_GROUP_NAME = "vectorize-group";
    public static final String KB_VECTORIZE_CONSUMER_PREFIX = "vectorize-consumer-";
    public static final String FIELD_KB_ID = "kbId";

    // ========== 简历分析 Stream 配置 ==========

    public static final String RESUME_ANALYZE_STREAM_KEY = "resume:analyze:stream";
    public static final String RESUME_ANALYZE_GROUP_NAME = "analyze-group";
    public static final String RESUME_ANALYZE_CONSUMER_PREFIX = "analyze-consumer-";
    public static final String FIELD_RESUME_ID = "resumeId";

    // ========== 面试评估 Stream 配置 ==========

    public static final String INTERVIEW_EVALUATE_STREAM_KEY = "interview:evaluate:stream";
    public static final String INTERVIEW_EVALUATE_GROUP_NAME = "evaluate-group";
    public static final String INTERVIEW_EVALUATE_CONSUMER_PREFIX = "evaluate-consumer-";
    public static final String FIELD_SESSION_ID = "sessionId";

    // ========== 语音面试评估 Stream 配置 ==========

    public static final String VOICE_EVALUATE_STREAM_KEY = "voice:evaluate:stream";
    public static final String VOICE_EVALUATE_GROUP_NAME = "voice-evaluate-group";
    public static final String VOICE_EVALUATE_CONSUMER_PREFIX = "voice-evaluate-consumer-";
    public static final String FIELD_VOICE_SESSION_ID = "voiceSessionId";

    // ========== 知识库问题生成 Stream 配置 ==========

    public static final String KB_QUESTION_GEN_STREAM_KEY = "knowledgebase:question-gen:stream";
    public static final String KB_QUESTION_GEN_GROUP_NAME = "question-gen-group";
    public static final String KB_QUESTION_GEN_CONSUMER_PREFIX = "question-gen-consumer-";
    public static final String FIELD_TASK_ID = "taskId";
    public static final String FIELD_DIFFICULTY = "difficulty";
    public static final String FIELD_QUESTION_COUNT = "questionCount";
    public static final String FIELD_FOLLOW_UP_COUNT = "followUpCount";
    public static final String FIELD_CATEGORY_LIMIT = "categoryLimit";
    public static final String FIELD_LLM_PROVIDER = "llmProvider";
}
