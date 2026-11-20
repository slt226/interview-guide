package interview.guide.modules.knowledgebase.repository;

import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.model.QuestionGenStatus;
import interview.guide.modules.knowledgebase.model.VectorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 知识库Repository
 *
 * 数据隔离约定：
 *   - 带 UserId 的方法用于 HTTP 入口，必须使用
 *   - 不带 UserId 的方法只留给异步消费者、定时任务等"系统身份"
 */
@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBaseEntity, Long> {

    // ==================== 用户视角（HTTP 入口用这些） ====================

    /**
     * 按哈希在当前用户名下查找（用于去重）
     */
    Optional<KnowledgeBaseEntity> findByUserIdAndFileHash(Long userId, String fileHash);

    /**
     * 按 id + 归属用户查找
     */
    Optional<KnowledgeBaseEntity> findByIdAndUserId(Long id, Long userId);

    /**
     * 当前用户的知识库列表（按上传时间倒序）
     */
    List<KnowledgeBaseEntity> findByUserIdOrderByUploadedAtDesc(Long userId);

    /**
     * 当前用户 + 向量化状态过滤
     */
    List<KnowledgeBaseEntity> findByUserIdAndVectorStatusOrderByUploadedAtDesc(Long userId, VectorStatus vectorStatus);

    /**
     * 当前用户 + 分类过滤
     */
    List<KnowledgeBaseEntity> findByUserIdAndCategoryOrderByUploadedAtDesc(Long userId, String category);

    /**
     * 当前用户 + 未分类
     */
    List<KnowledgeBaseEntity> findByUserIdAndCategoryIsNullOrderByUploadedAtDesc(Long userId);

    /**
     * 当前用户的知识库分类列表
     */
    @Query("SELECT DISTINCT k.category FROM KnowledgeBaseEntity k "
        + "WHERE k.userId = :userId AND k.category IS NOT NULL ORDER BY k.category")
    List<String> findDistinctCategoriesByUserId(@Param("userId") Long userId);

    /**
     * 当前用户内按名称/文件名模糊搜索
     */
    @Query("SELECT k FROM KnowledgeBaseEntity k WHERE k.userId = :userId "
        + "AND (LOWER(k.name) LIKE LOWER(CONCAT('%', :keyword, '%')) "
        + "OR LOWER(k.originalFilename) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
        + "ORDER BY k.uploadedAt DESC")
    List<KnowledgeBaseEntity> searchByUserIdAndKeyword(@Param("userId") Long userId,
                                                       @Param("keyword") String keyword);

    /**
     * 按 id 集合 + 归属用户批量取（用于校验"这批知识库是不是我的"）
     */
    List<KnowledgeBaseEntity> findByIdInAndUserId(List<Long> ids, Long userId);

    /**
     * 当前用户的知识库总数
     */
    long countByUserId(Long userId);

    /**
     * 当前用户 + 向量化状态计数
     */
    long countByUserIdAndVectorStatus(Long userId, VectorStatus vectorStatus);

    /**
     * 当前用户的访问次数总和
     */
    @Query("SELECT COALESCE(SUM(k.accessCount), 0) FROM KnowledgeBaseEntity k WHERE k.userId = :userId")
    long sumAccessCountByUserId(@Param("userId") Long userId);

    // ==================== 系统视角（异步任务 / 定时任务） ====================

    /**
     * 锁定知识库行，用于串行化同一知识库的题目生成状态迁移。
     * 系统身份使用，不加 owner 条件。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT k FROM KnowledgeBaseEntity k WHERE k.id = :id")
    Optional<KnowledgeBaseEntity> findByIdForUpdate(@Param("id") Long id);

    /**
     * 批量增加知识库提问计数（id 来自已经校验过归属的会话）
     */
    @Modifying
    @Query("UPDATE KnowledgeBaseEntity k SET k.questionCount = k.questionCount + 1 WHERE k.id IN :ids")
    int incrementQuestionCountBatch(@Param("ids") List<Long> ids);

    /**
     * 按向量化状态查找知识库（后台补处理用）
     */
    List<KnowledgeBaseEntity> findByVectorStatusOrderByUploadedAtDesc(VectorStatus vectorStatus);

    /**
     * 找出状态卡住超过阈值的问题生成任务（恢复调度用）
     */
    @Query("SELECT k FROM KnowledgeBaseEntity k "
        + "WHERE k.questionGenStatus = :status "
        + "AND (k.questionGenUpdatedAt IS NULL OR k.questionGenUpdatedAt < :threshold)")
    List<KnowledgeBaseEntity> findStaleQuestionGenerationTasks(
        @Param("status") QuestionGenStatus status,
        @Param("threshold") LocalDateTime threshold);
}
