package interview.guide.modules.knowledgebase.repository;

import interview.guide.modules.knowledgebase.model.RagChatSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * RAG聊天会话Repository
 *
 * 所有 HTTP 入口使用带 UserId 的方法；不带 UserId 的仅限系统身份（如删除知识库时清理关联）。
 */
@Repository
public interface RagChatSessionRepository extends JpaRepository<RagChatSessionEntity, Long> {

    /**
     * 当前用户的会话列表（置顶优先，再按更新时间倒序）
     */
    @Query("SELECT s FROM RagChatSessionEntity s WHERE s.userId = :userId "
        + "ORDER BY s.isPinned DESC, s.updatedAt DESC")
    List<RagChatSessionEntity> findAllByUserIdOrderByPinnedAndUpdatedAtDesc(@Param("userId") Long userId);

    /**
     * 按 id + 归属用户获取会话（带知识库，不带消息）
     */
    @Query("SELECT s FROM RagChatSessionEntity s LEFT JOIN FETCH s.knowledgeBases "
        + "WHERE s.id = :id AND s.userId = :userId")
    Optional<RagChatSessionEntity> findByIdAndUserIdWithKnowledgeBases(@Param("id") Long id,
                                                                      @Param("userId") Long userId);

    /**
     * 按 id + 归属用户获取会话（仅实体，用于改名/置顶/删除等轻量操作）
     */
    Optional<RagChatSessionEntity> findByIdAndUserId(Long id, Long userId);

    /**
     * 根据知识库ID查找相关会话
     * 仅用于"删除知识库前清理关联"，知识库归属已在上游校验
     */
    @Query("SELECT DISTINCT s FROM RagChatSessionEntity s JOIN s.knowledgeBases kb "
        + "WHERE kb.id IN :kbIds ORDER BY s.updatedAt DESC")
    List<RagChatSessionEntity> findByKnowledgeBaseIds(@Param("kbIds") List<Long> knowledgeBaseIds);
}
