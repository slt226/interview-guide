package interview.guide.modules.voiceinterview.repository;

import interview.guide.modules.voiceinterview.model.VoiceInterviewSessionEntity;
import interview.guide.modules.voiceinterview.model.VoiceInterviewSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 语音面试会话Repository
 *
 * user_id 已从 varchar 改为 bigint，与 app_user.id 对齐；
 * 带 userId 的方法给 HTTP 入口用，不带的是评估补偿/清理这类系统任务。
 */
@Repository
public interface VoiceInterviewSessionRepository extends JpaRepository<VoiceInterviewSessionEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM VoiceInterviewSessionEntity s WHERE s.id = :sessionId")
    Optional<VoiceInterviewSessionEntity> findByIdForUpdate(@Param("sessionId") Long sessionId);

    /**
     * 按 id + 归属用户查找
     */
    Optional<VoiceInterviewSessionEntity> findByIdAndUserId(Long id, Long userId);

    /**
     * 根据用户ID查找所有会话，按开始时间倒序
     */
    List<VoiceInterviewSessionEntity> findByUserIdOrderByStartTimeDesc(Long userId);

    /**
     * 根据用户ID查找所有会话，按更新时间倒序
     */
    List<VoiceInterviewSessionEntity> findByUserIdOrderByUpdatedAtDesc(Long userId);

    /**
     * 根据用户与状态查找会话，按更新时间倒序
     */
    List<VoiceInterviewSessionEntity> findByUserIdAndStatusOrderByUpdatedAtDesc(
        Long userId,
        VoiceInterviewSessionStatus status
    );

    /**
     * 查找指定状态且结束时间早于给定时间的会话
     * Note: Queries the AsyncTaskStatus field, not InterviewPhase
     */
    Optional<VoiceInterviewSessionEntity> findByStatusAndEndTimeBefore(
        interview.guide.common.model.AsyncTaskStatus status,
        LocalDateTime time
    );

    List<VoiceInterviewSessionEntity> findByStatusAndStartTimeBefore(
        VoiceInterviewSessionStatus status,
        LocalDateTime time
    );

    List<VoiceInterviewSessionEntity> findByEvaluateStatusAndUpdatedAtBefore(
        interview.guide.common.model.AsyncTaskStatus evaluateStatus,
        LocalDateTime time
    );
}
