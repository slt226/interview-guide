package interview.guide.modules.resume.repository;

import interview.guide.modules.resume.model.ResumeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 简历Repository
 *
 * 数据隔离约定：所有对外（HTTP）可见的查询都必须带 userId 条件。
 * 只有异步消费者、定时任务这类"系统身份"才使用不带 userId 的方法。
 */
@Repository
public interface ResumeRepository extends JpaRepository<ResumeEntity, Long> {

    /**
     * 按哈希在当前用户名下查找（用于去重）
     * 注意：去重范围是"用户内"，不能跨用户
     */
    Optional<ResumeEntity> findByUserIdAndFileHash(Long userId, String fileHash);

    /**
     * 当前用户的简历列表（按上传时间倒序）
     */
    List<ResumeEntity> findByUserIdOrderByUploadedAtDesc(Long userId);

    /**
     * 按 id + 归属用户查找
     * owner 直接写进 where 条件，从 SQL 层就不可能取到别人的数据
     */
    Optional<ResumeEntity> findByIdAndUserId(Long id, Long userId);
}
