package interview.guide.infrastructure.mapper;

import interview.guide.common.async.dto.DeadLetterResponse;
import interview.guide.common.async.model.AsyncDeadLetterEntity;
import interview.guide.common.async.model.DeadLetterStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link DeadLetterMapper}（MapStruct 编译期生成的实现类）的实体到响应对象映射。
 * <p>
 * 核心断言点：DeadLetterResponse 类型本身不含 payload 字段（编译期已保证不暴露原始消息体），
 * 其余 13 个字段应一一正确映射。
 */
@DisplayName("死信实体到响应对象映射")
class DeadLetterMapperTest {

  private final DeadLetterMapper mapper = new DeadLetterMapperImpl();

  @Test
  @DisplayName("实体字段正确映射到响应对象（payload 大字段被忽略，不暴露给前端）")
  void shouldMapEntityToResponseWithoutPayload() {
    AsyncDeadLetterEntity entity = new AsyncDeadLetterEntity();
    entity.setId(1L);
    entity.setStreamKey("test:stream");
    entity.setGroupName("test-group");
    entity.setMessageId("1-0");
    entity.setTaskType("INTERVIEW_EVALUATE");
    entity.setPayloadIdentifier("task-1");
    entity.setPayload("{\"large\":\"payload-content-should-not-leak\"}");
    entity.setStatus(DeadLetterStatus.DEAD);
    entity.setErrorMessage("评估失败");
    entity.setRetryCount(3);
    entity.setReplayCount(1);
    LocalDateTime now = LocalDateTime.now();
    entity.setCreatedAt(now);
    entity.setLastReplayAt(now);
    entity.setUpdatedAt(now);

    DeadLetterResponse response = mapper.toResponse(entity);

    assertThat(response.getId()).isEqualTo(1L);
    assertThat(response.getStreamKey()).isEqualTo("test:stream");
    assertThat(response.getGroupName()).isEqualTo("test-group");
    assertThat(response.getMessageId()).isEqualTo("1-0");
    assertThat(response.getTaskType()).isEqualTo("INTERVIEW_EVALUATE");
    assertThat(response.getPayloadIdentifier()).isEqualTo("task-1");
    assertThat(response.getStatus()).isEqualTo(DeadLetterStatus.DEAD);
    assertThat(response.getErrorMessage()).isEqualTo("评估失败");
    assertThat(response.getRetryCount()).isEqualTo(3);
    assertThat(response.getReplayCount()).isEqualTo(1);
    assertThat(response.getCreatedAt()).isEqualTo(now);
    assertThat(response.getLastReplayAt()).isEqualTo(now);
    assertThat(response.getUpdatedAt()).isEqualTo(now);
    // DeadLetterResponse 类型本身没有 payload 字段，编译期已保证 payload 不外泄
  }

  @Test
  @DisplayName("列表批量映射")
  void shouldMapEntityList() {
    AsyncDeadLetterEntity entity = new AsyncDeadLetterEntity();
    entity.setId(1L);
    entity.setStreamKey("test:stream");
    entity.setStatus(DeadLetterStatus.DEAD);

    List<DeadLetterResponse> responses = mapper.toResponseList(List.of(entity));

    assertThat(responses).hasSize(1);
    assertThat(responses.get(0).getId()).isEqualTo(1L);
  }
}
