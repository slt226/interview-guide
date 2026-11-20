package interview.guide.common.async;

import interview.guide.common.async.service.AsyncDeadLetterService;
import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.infrastructure.redis.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.stream.StreamMessageId;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 {@code AbstractStreamConsumer#processMessage} 的消费幂等与死信归档逻辑。
 * <p>
 * 通过匿名子类 + 反射调用 private 的 processMessage，mock 掉 RedisService 的
 * trySetIfAbsent / delete / streamAck，从而覆盖正常运行时难以触发的分支：
 * 重复投递被拦截、失败重试、重试耗尽归档死信。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("异步消费者：消费幂等与死信归档")
class AbstractStreamConsumerIdempotencyTest {

  @Mock
  private RedisService redisService;
  @Mock
  private AsyncDeadLetterService deadLetterService;
  @Mock
  private DeadLetterMetrics deadLetterMetrics;

  private TestConsumer consumer;

  @BeforeEach
  void setUp() throws Exception {
    consumer = new TestConsumer(redisService);
    injectDeadLetterService(consumer, deadLetterService);
    injectDeadLetterMetrics(consumer, deadLetterMetrics);
  }

  @Nested
  @DisplayName("消费幂等拦截")
  class Idempotency {

    @Test
    @DisplayName("重复投递（幂等键已存在）时跳过业务处理并直接 ACK")
    void shouldSkipDuplicateDelivery() throws Exception {
      when(redisService.trySetIfAbsent(anyString(), anyString(), any(Duration.class)))
          .thenReturn(false);

      invokeProcessMessage(consumer, Map.of("id", "task-1"));

      assertThat(consumer.businessCallCount.get()).isZero();
      assertThat(consumer.markCompletedCalled.get()).isFalse();
      verify(redisService).streamAck(eq("test:stream"), eq("test-group"), any(StreamMessageId[].class));
    }

    @Test
    @DisplayName("处理成功后保留幂等键（不删除），并完成 ACK")
    void shouldKeepIdempotencyKeyOnSuccess() throws Exception {
      when(redisService.trySetIfAbsent(anyString(), anyString(), any(Duration.class)))
          .thenReturn(true);

      invokeProcessMessage(consumer, Map.of("id", "task-1"));

      assertThat(consumer.businessCallCount.get()).isEqualTo(1);
      assertThat(consumer.markCompletedCalled.get()).isTrue();
      verify(redisService, never()).delete(anyString());
      verify(redisService).streamAck(eq("test:stream"), eq("test-group"), any(StreamMessageId[].class));
    }
  }

  @Nested
  @DisplayName("失败重试与死信归档")
  class FailureHandling {

    @Test
    @DisplayName("失败且未达重试上限时：删除幂等键并重新入队重试，不写死信")
    void shouldRetryInsteadOfDeadLetterWhenUnderLimit() throws Exception {
      when(redisService.trySetIfAbsent(anyString(), anyString(), any(Duration.class)))
          .thenReturn(true);
      consumer.failBusinessWith(new RuntimeException("boom"));

      invokeProcessMessage(consumer, Map.of("id", "task-1", "retryCount", "0"));

      assertThat(consumer.retryMessageCallCount.get()).isEqualTo(1);
      assertThat(consumer.markFailedCalled.get()).isFalse();
      verify(redisService).delete(anyString());
      verify(deadLetterService, never()).saveDeadLetter(
          anyString(), anyString(), anyString(), anyString(), anyMap(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("失败且达到重试上限时：标记 FAILED 并归档死信，不再重试")
    void shouldPersistDeadLetterWhenRetryExhausted() throws Exception {
      when(redisService.trySetIfAbsent(anyString(), anyString(), any(Duration.class)))
          .thenReturn(true);
      consumer.failBusinessWith(new RuntimeException("boom"));

      invokeProcessMessage(consumer, Map.of(
          "id", "task-1",
          "retryCount", String.valueOf(AsyncTaskStreamConstants.MAX_RETRY_COUNT)));

      assertThat(consumer.markFailedCalled.get()).isTrue();
      assertThat(consumer.retryMessageCallCount.get()).isZero();
      verify(redisService).delete(anyString());
      verify(deadLetterService).saveDeadLetter(
          eq("test:stream"), eq("test-group"), anyString(), eq("test:stream"),
          anyMap(), eq("task-1"), anyString(), eq(AsyncTaskStreamConstants.MAX_RETRY_COUNT));
    }
  }

  // ========== 测试消费者（匿名子类实现抽象方法，用原子量记录副作用） ==========

  private static class TestConsumer extends AbstractStreamConsumer<String> {

    final AtomicInteger businessCallCount = new AtomicInteger();
    final AtomicInteger retryMessageCallCount = new AtomicInteger();
    final AtomicBoolean markCompletedCalled = new AtomicBoolean();
    final AtomicBoolean markFailedCalled = new AtomicBoolean();
    private volatile RuntimeException businessException;

    TestConsumer(RedisService redisService) {
      super(redisService);
    }

    void failBusinessWith(RuntimeException e) {
      this.businessException = e;
    }

    @Override
    protected String taskDisplayName() {
      return "测试任务";
    }

    @Override
    protected String streamKey() {
      return "test:stream";
    }

    @Override
    protected String groupName() {
      return "test-group";
    }

    @Override
    protected String consumerPrefix() {
      return "test-consumer";
    }

    @Override
    protected String threadName() {
      return "test-thread";
    }

    @Override
    protected String parsePayload(StreamMessageId messageId, Map<String, String> data) {
      return data.get("id");
    }

    @Override
    protected String payloadIdentifier(String payload) {
      return payload;
    }

    @Override
    protected void markProcessing(String payload) {
      // 无状态，测试不需要
    }

    @Override
    protected void processBusiness(String payload) {
      businessCallCount.incrementAndGet();
      if (businessException != null) {
        throw businessException;
      }
    }

    @Override
    protected void markCompleted(String payload) {
      markCompletedCalled.set(true);
    }

    @Override
    protected void markFailed(String payload, String error) {
      markFailedCalled.set(true);
    }

    @Override
    protected void retryMessage(String payload, int retryCount) {
      retryMessageCallCount.incrementAndGet();
    }
  }

  // ========== 反射辅助 ==========

  private void injectDeadLetterService(AbstractStreamConsumer<?> consumer, AsyncDeadLetterService service)
      throws Exception {
    Field field = AbstractStreamConsumer.class.getDeclaredField("deadLetterService");
    field.setAccessible(true);
    field.set(consumer, service);
  }

  private void injectDeadLetterMetrics(AbstractStreamConsumer<?> consumer, DeadLetterMetrics metrics)
      throws Exception {
    Field field = AbstractStreamConsumer.class.getDeclaredField("deadLetterMetrics");
    field.setAccessible(true);
    field.set(consumer, metrics);
  }

  private void invokeProcessMessage(AbstractStreamConsumer<?> consumer, Map<String, String> data)
      throws Exception {
    Method method = AbstractStreamConsumer.class.getDeclaredMethod(
        "processMessage", StreamMessageId.class, Map.class);
    method.setAccessible(true);
    method.invoke(consumer, new StreamMessageId(1, 0), new HashMap<>(data));
  }
}
