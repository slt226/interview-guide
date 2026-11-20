package interview.guide.common.async;

import interview.guide.common.async.model.DeadLetterStatus;
import interview.guide.common.async.repository.AsyncDeadLetterRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 异步任务可靠性指标的集中埋点组件（Micrometer + Prometheus）。
 * <p>
 * 覆盖方案 A 的三类可观测指标（对应设计文档 §二 目标 3）：
 * <ul>
 *   <li>{@link #incrementIdempotencyHit}  幂等拦截命中数 —— 证明「重复投递导致的重复副作用 = 0」；</li>
 *   <li>{@link #incrementDeadLetterArchived} 死信归档数 —— 失败任务 100% 入死信的规模；</li>
 *   <li>{@link #incrementReplayed} / {@link #incrementResolved} 重投 / 人工解决数 —— 恢复率与运营闭环。</li>
 * </ul>
 * 命名对齐项目既有规范（{@code app.<域>.<组件>.<指标>}，参考 StructuredOutputInvoker 的
 * {@code app.ai.structured_output.*}）。
 * <p>
 * MeterRegistry 采用 {@code @Autowired(required = false)} 可选注入：测试环境或未引入 actuator 时
 * 不会因缺少 bean 而启动失败，埋点调用自动降级为 no-op（见 {@link #increment}）。
 */
@Component
public class DeadLetterMetrics {

    public static final String METRIC_IDEMPOTENCY_HITS = "app.async.idempotency.hits";
    // 注意：不要用 `created` 结尾 —— `_created` 是 Prometheus 的保留后缀（Counter 创建时间戳），
    // Micrometer 的 Prometheus 命名约定会剥离它，导致指标名变成 app_async_dead_letter_total。
    public static final String METRIC_DEAD_LETTER_ARCHIVED = "app.async.dead_letter.archived";
    public static final String METRIC_DEAD_LETTER_REPLAYED = "app.async.dead_letter.replayed";
    public static final String METRIC_DEAD_LETTER_RESOLVED = "app.async.dead_letter.resolved";
    public static final String METRIC_DEAD_LETTER_PENDING = "app.async.dead_letter.pending";

    private static final String TAG_TASK_TYPE = "task_type";
    private static final String UNKNOWN_TASK_TYPE = "unknown";

    private final AsyncDeadLetterRepository repository;
    private final MeterRegistry meterRegistry;

    public DeadLetterMetrics(
        AsyncDeadLetterRepository repository,
        @Autowired(required = false) MeterRegistry meterRegistry
    ) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 注册当前待处理（DEAD）死信总数的 Gauge。Gauge 为懒求值，仅在 Prometheus 拉取时查询一次
     * {@code countByStatus(DEAD)}，因此 @PostConstruct 时无需关心表是否已就绪。
     */
    @PostConstruct
    void registerPendingGauge() {
        if (meterRegistry == null) {
            return;
        }
        Gauge.builder(METRIC_DEAD_LETTER_PENDING, repository,
                r -> r.countByStatus(DeadLetterStatus.DEAD))
            .description("当前待处理（DEAD）死信总数")
            .register(meterRegistry);
    }

    public void incrementIdempotencyHit(String taskType) {
        increment(METRIC_IDEMPOTENCY_HITS, taskType);
    }

    public void incrementDeadLetterArchived(String taskType) {
        increment(METRIC_DEAD_LETTER_ARCHIVED, taskType);
    }

    public void incrementReplayed(String taskType) {
        increment(METRIC_DEAD_LETTER_REPLAYED, taskType);
    }

    public void incrementResolved(String taskType) {
        increment(METRIC_DEAD_LETTER_RESOLVED, taskType);
    }

    private void increment(String metricName, String taskType) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(metricName, Tags.of(TAG_TASK_TYPE, normalizeTaskType(taskType))).increment();
    }

    private static String normalizeTaskType(String taskType) {
        return taskType == null || taskType.isBlank() ? UNKNOWN_TASK_TYPE : taskType;
    }
}
