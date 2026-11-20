package interview.guide.common.async.service;

import interview.guide.common.async.DeadLetterMetrics;
import interview.guide.common.async.dto.DeadLetterResponse;
import interview.guide.common.async.model.AsyncDeadLetterEntity;
import interview.guide.common.async.model.DeadLetterStatus;
import interview.guide.common.async.repository.AsyncDeadLetterRepository;
import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.common.exception.BusinessException;
import interview.guide.infrastructure.mapper.DeadLetterMapper;
import interview.guide.infrastructure.redis.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link AsyncDeadLetterService} 的重投 / 丢弃 / 列表查询逻辑。
 * <p>
 * Repository 与 RedisService 均 mock 掉，ObjectMapper 使用真实实例（序列化/反序列化是纯逻辑）。
 * 重点验证：重投时 retryCount 被重置为 0 重新入队、状态流转、以及非法操作的异常分支。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("异步任务死信服务")
class AsyncDeadLetterServiceTest {

  @Mock
  private AsyncDeadLetterRepository repository;
  @Mock
  private RedisService redisService;
  @Mock
  private DeadLetterMapper deadLetterMapper;
  @Mock
  private DeadLetterMetrics deadLetterMetrics;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private AsyncDeadLetterService service;

  @BeforeEach
  void setUp() {
    service = new AsyncDeadLetterService(repository, redisService, objectMapper, deadLetterMapper, deadLetterMetrics);
  }

  private AsyncDeadLetterEntity buildEntity(DeadLetterStatus status) {
    AsyncDeadLetterEntity entity = new AsyncDeadLetterEntity();
    entity.setId(1L);
    entity.setStreamKey("test:stream");
    entity.setGroupName("test-group");
    entity.setMessageId("1-0");
    entity.setTaskType("INTERVIEW_EVALUATE");
    entity.setPayloadIdentifier("task-1");
    entity.setPayload("{\"id\":\"task-1\",\"retryCount\":\"3\"}");
    entity.setStatus(status);
    entity.setRetryCount(3);
    entity.setReplayCount(0);
    return entity;
  }

  @Nested
  @DisplayName("重投")
  class Replay {

    @Test
    @DisplayName("重投成功：重置重试次数并重新投递到 Stream，状态变为 REPLAYED")
    @SuppressWarnings("unchecked")
    void shouldReplayDeadLetter() {
      AsyncDeadLetterEntity entity = buildEntity(DeadLetterStatus.DEAD);
      when(repository.findById(1L)).thenReturn(Optional.of(entity));
      when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      service.replay(1L);

      assertThat(entity.getStatus()).isEqualTo(DeadLetterStatus.REPLAYED);
      assertThat(entity.getReplayCount()).isEqualTo(1);
      assertThat(entity.getLastReplayAt()).isNotNull();

      ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
      verify(redisService).streamAdd(
          eq("test:stream"), captor.capture(), eq(AsyncTaskStreamConstants.STREAM_MAX_LEN));
      assertThat(captor.getValue())
          .containsEntry(AsyncTaskStreamConstants.FIELD_RETRY_COUNT, "0");
    }

    @Test
    @DisplayName("重投不存在的死信时抛出 DEAD_LETTER_NOT_FOUND")
    void shouldThrowWhenNotFound() {
      when(repository.findById(99L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.replay(99L))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("死信ID: 99");
    }

    @Test
    @DisplayName("重投已解决的死信时抛出 DEAD_LETTER_STATUS_INVALID")
    void shouldThrowWhenAlreadyResolved() {
      when(repository.findById(1L)).thenReturn(Optional.of(buildEntity(DeadLetterStatus.RESOLVED)));

      assertThatThrownBy(() -> service.replay(1L))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("已人工解决");
    }
  }

  @Nested
  @DisplayName("丢弃")
  class Discard {

    @Test
    @DisplayName("丢弃待处理死信后状态变为 RESOLVED")
    void shouldResolveDeadLetter() {
      AsyncDeadLetterEntity entity = buildEntity(DeadLetterStatus.DEAD);
      when(repository.findById(1L)).thenReturn(Optional.of(entity));
      when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      service.discard(1L);

      assertThat(entity.getStatus()).isEqualTo(DeadLetterStatus.RESOLVED);
      verify(repository).save(entity);
    }

    @Test
    @DisplayName("丢弃已解决的死信时抛出 DEAD_LETTER_STATUS_INVALID")
    void shouldThrowWhenResolvingResolved() {
      when(repository.findById(1L)).thenReturn(Optional.of(buildEntity(DeadLetterStatus.RESOLVED)));

      assertThatThrownBy(() -> service.discard(1L))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("已处于 RESOLVED");
    }
  }

  @Nested
  @DisplayName("列表查询")
  class ListQuery {

    @Test
    @DisplayName("状态为空时查询全部，并转换为响应对象")
    void shouldListAllWhenStatusNull() {
      List<AsyncDeadLetterEntity> entities = List.of(buildEntity(DeadLetterStatus.DEAD));
      when(repository.findAll()).thenReturn(entities);
      when(deadLetterMapper.toResponseList(entities)).thenReturn(List.of(new DeadLetterResponse()));

      List<DeadLetterResponse> result = service.listResponses(null);

      assertThat(result).hasSize(1);
      verify(repository).findAll();
      verify(deadLetterMapper).toResponseList(entities);
    }

    @Test
    @DisplayName("指定状态时按状态过滤查询")
    void shouldListByStatus() {
      when(repository.findByStatus(DeadLetterStatus.DEAD)).thenReturn(List.of());
      when(deadLetterMapper.toResponseList(anyList())).thenReturn(List.of());

      service.listResponses(DeadLetterStatus.DEAD);

      verify(repository).findByStatus(DeadLetterStatus.DEAD);
      verify(repository, never()).findAll();
    }
  }
}
