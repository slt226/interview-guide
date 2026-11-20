package interview.guide.modules.interview.repository;

import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.model.InterviewSessionEntity.SessionStatus;
import interview.guide.modules.resume.model.ResumeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 面试会话Repository
 *
 * 对外查询一律使用 sessionId + userId 组合；
 * 只带 sessionId 的方法留给异步评估消费者等系统身份。
 */
@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSessionEntity, Long> {

    // ==================== 用户视角 ====================

    /**
     * 按会话ID + 归属用户查找
     */
    Optional<InterviewSessionEntity> findBySessionIdAndUserId(String sessionId, Long userId);

    /**
     * 当前用户的面试记录（按创建时间倒序）
     */
    List<InterviewSessionEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * 当前用户某个简历下未完成的面试
     */
    Optional<InterviewSessionEntity> findFirstByUserIdAndResumeIdAndStatusInOrderByCreatedAtDesc(
        Long userId, Long resumeId, List<SessionStatus> statuses);

    /**
     * 当前用户最近的面试记录（通用模式历史题去重）
     */
    List<InterviewSessionEntity> findTop10ByUserIdAndSkillIdOrderByCreatedAtDesc(Long userId, String skillId);

    /**
     * 当前用户 + 简历 + skillId 的最近面试记录
     */
    List<InterviewSessionEntity> findTop10ByUserIdAndResumeIdAndSkillIdOrderByCreatedAtDesc(
        Long userId, Long resumeId, String skillId);

    /**
     * 幂等键只在"当前用户内"有效，避免 A 的 requestId 把 B 的创建请求顶掉
     */
    Optional<InterviewSessionEntity> findByRequestIdAndUserId(String requestId, Long userId);

    // ==================== 系统视角（异步评估 / 清理） ====================

    /**
     * 按会话ID查找（异步消费者使用，此时没有登录态）
     */
    Optional<InterviewSessionEntity> findBySessionId(String sessionId);

    Optional<InterviewSessionEntity> findByRequestId(String requestId);

    /**
     * 根据会话ID查找（同时加载关联的简历）
     */
    @Query("SELECT s FROM InterviewSessionEntity s LEFT JOIN FETCH s.resume WHERE s.sessionId = :sessionId")
    Optional<InterviewSessionEntity> findBySessionIdWithResume(@Param("sessionId") String sessionId);

    /**
     * 根据简历ID查找所有面试记录（简历归属已在上游校验）
     */
    List<InterviewSessionEntity> findByResumeIdOrderByCreatedAtDesc(Long resumeId);

    List<InterviewSessionEntity> findByResumeOrderByCreatedAtDesc(ResumeEntity resume);
}
