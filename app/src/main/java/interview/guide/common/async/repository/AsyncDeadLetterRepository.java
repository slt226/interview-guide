package interview.guide.common.async.repository;

import interview.guide.common.async.model.AsyncDeadLetterEntity;
import interview.guide.common.async.model.DeadLetterStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AsyncDeadLetterRepository extends JpaRepository<AsyncDeadLetterEntity, Long> {
    // 按状态筛选（如查所有 DEAD 待重投记录，供管理页列表）
    List<AsyncDeadLetterEntity> findByStatus(DeadLetterStatus status);

    // 按业务唯一标识查询（如 knowledge_base_id / session_id），重投幂等去重时判断是否已存在
    Optional<AsyncDeadLetterEntity> findByPayloadIdentifier(String payloadIdentifier);

    // 按状态统计数量（供 DeadLetterMetrics 的 Gauge 计算当前待处理死信积压）
    long countByStatus(DeadLetterStatus status);
}
