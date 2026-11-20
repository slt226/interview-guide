package interview.guide.modules.interviewschedule.repository;

import interview.guide.modules.interviewschedule.model.InterviewScheduleEntity;
import interview.guide.modules.interviewschedule.model.InterviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 面试日程Repository
 *
 * 带 userId 的方法给 HTTP 入口用；不带的是 ScheduleStatusUpdater 这类系统任务。
 */
@Repository
public interface InterviewScheduleRepository extends JpaRepository<InterviewScheduleEntity, Long> {

    // ==================== 用户视角 ====================

    Optional<InterviewScheduleEntity> findByIdAndUserId(Long id, Long userId);

    List<InterviewScheduleEntity> findByUserIdOrderByInterviewTimeAsc(Long userId);

    List<InterviewScheduleEntity> findByUserIdAndStatusOrderByInterviewTimeAsc(Long userId, InterviewStatus status);

    List<InterviewScheduleEntity> findByUserIdAndInterviewTimeBetweenOrderByInterviewTimeAsc(
        Long userId, LocalDateTime start, LocalDateTime end);

    // ==================== 系统视角（状态自动流转） ====================

    List<InterviewScheduleEntity> findByStatusAndInterviewTimeBefore(InterviewStatus status, LocalDateTime time);

    List<InterviewScheduleEntity> findByStatus(InterviewStatus status);

    List<InterviewScheduleEntity> findByInterviewTimeBetween(LocalDateTime start, LocalDateTime end);

    @Modifying
    @Query("UPDATE InterviewScheduleEntity e SET e.status = :newStatus "
        + "WHERE e.status = :oldStatus AND e.interviewTime < :cutoff")
    int updateStatusByStatusAndInterviewTimeBefore(
        @Param("newStatus") InterviewStatus newStatus,
        @Param("oldStatus") InterviewStatus oldStatus,
        @Param("cutoff") LocalDateTime cutoff);
}
