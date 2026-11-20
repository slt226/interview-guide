package interview.guide.modules.knowledgebase.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.security.CurrentUser;
import interview.guide.infrastructure.file.FileStorageService;
import interview.guide.infrastructure.mapper.KnowledgeBaseMapper;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseListItemDTO;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseStatsDTO;
import interview.guide.modules.knowledgebase.model.RagChatMessageEntity.MessageType;
import interview.guide.modules.knowledgebase.model.VectorStatus;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import interview.guide.modules.knowledgebase.repository.RagChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 知识库查询服务
 * 负责知识库列表和详情的查询
 *
 * 数据隔离：本类所有方法都是 HTTP 入口调用，一律带 owner 条件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseListService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final RagChatMessageRepository ragChatMessageRepository;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final FileStorageService fileStorageService;

    /**
     * 获取当前用户的知识库列表（支持状态过滤和排序）
     */
    public List<KnowledgeBaseListItemDTO> listKnowledgeBases(VectorStatus vectorStatus, String sortBy) {
        Long userId = CurrentUser.requireId();
        List<KnowledgeBaseEntity> entities;

        if (vectorStatus != null) {
            entities = knowledgeBaseRepository
                .findByUserIdAndVectorStatusOrderByUploadedAtDesc(userId, vectorStatus);
        } else {
            entities = knowledgeBaseRepository.findByUserIdOrderByUploadedAtDesc(userId);
        }

        // 如果指定了排序字段，在内存中排序
        if (sortBy != null && !sortBy.isBlank() && !sortBy.equalsIgnoreCase("time")) {
            entities = sortEntities(entities, sortBy);
        }

        return knowledgeBaseMapper.toListItemDTOList(entities);
    }

    /**
     * 获取当前用户的知识库列表（保持向后兼容）
     */
    public List<KnowledgeBaseListItemDTO> listKnowledgeBases() {
        return listKnowledgeBases(null, null);
    }

    /**
     * 按向量化状态获取当前用户的知识库列表
     */
    public List<KnowledgeBaseListItemDTO> listKnowledgeBasesByStatus(VectorStatus vectorStatus) {
        return listKnowledgeBases(vectorStatus, null);
    }

    /**
     * 根据ID获取当前用户的知识库详情
     * 别人的知识库一律当作不存在，避免泄露资源存在性
     */
    public Optional<KnowledgeBaseListItemDTO> getKnowledgeBase(Long id) {
        return knowledgeBaseRepository.findByIdAndUserId(id, CurrentUser.requireId())
            .map(knowledgeBaseMapper::toListItemDTO);
    }

    /**
     * 根据ID获取当前用户的知识库实体（用于删除等操作）
     */
    public Optional<KnowledgeBaseEntity> getKnowledgeBaseEntity(Long id) {
        return knowledgeBaseRepository.findByIdAndUserId(id, CurrentUser.requireId());
    }

    /**
     * 根据ID列表获取知识库名称列表（只含当前用户的）
     */
    public List<String> getKnowledgeBaseNames(List<Long> ids) {
        Long userId = CurrentUser.requireId();
        return ids.stream()
            .map(id -> knowledgeBaseRepository.findByIdAndUserId(id, userId)
                .map(KnowledgeBaseEntity::getName)
                .orElse("未知知识库"))
            .toList();
    }

    // ========== 分类管理 ==========

    /**
     * 获取当前用户的所有分类
     */
    public List<String> getAllCategories() {
        return knowledgeBaseRepository.findDistinctCategoriesByUserId(CurrentUser.requireId());
    }

    /**
     * 根据分类获取当前用户的知识库列表
     */
    public List<KnowledgeBaseListItemDTO> listByCategory(String category) {
        Long userId = CurrentUser.requireId();
        List<KnowledgeBaseEntity> entities;
        if (category == null || category.isBlank()) {
            entities = knowledgeBaseRepository.findByUserIdAndCategoryIsNullOrderByUploadedAtDesc(userId);
        } else {
            entities = knowledgeBaseRepository.findByUserIdAndCategoryOrderByUploadedAtDesc(userId, category);
        }
        return knowledgeBaseMapper.toListItemDTOList(entities);
    }

    /**
     * 更新知识库分类（只能改自己的）
     */
    @Transactional
    public void updateCategory(Long id, String category) {
        KnowledgeBaseEntity entity = knowledgeBaseRepository.findByIdAndUserId(id, CurrentUser.requireId())
            .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在"));
        entity.setCategory(category != null && !category.isBlank() ? category : null);
        knowledgeBaseRepository.save(entity);
        log.info("更新知识库分类: id={}, category={}", id, category);
    }

    // ========== 搜索功能 ==========

    /**
     * 按关键词搜索当前用户的知识库
     */
    public List<KnowledgeBaseListItemDTO> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return listKnowledgeBases();
        }
        return knowledgeBaseMapper.toListItemDTOList(
            knowledgeBaseRepository.searchByUserIdAndKeyword(CurrentUser.requireId(), keyword.trim())
        );
    }

    // ========== 排序功能 ==========

    /**
     * 按指定字段排序获取当前用户的知识库列表
     */
    public List<KnowledgeBaseListItemDTO> listSorted(String sortBy) {
        return listKnowledgeBases(null, sortBy);
    }

    /**
     * 在内存中对实体列表排序
     */
    private List<KnowledgeBaseEntity> sortEntities(List<KnowledgeBaseEntity> entities, String sortBy) {
        return switch (sortBy.toLowerCase()) {
            case "size" -> entities.stream()
                .sorted((a, b) -> Long.compare(b.getFileSize(), a.getFileSize()))
                .toList();
            case "access" -> entities.stream()
                .sorted((a, b) -> Integer.compare(b.getAccessCount(), a.getAccessCount()))
                .toList();
            case "question" -> entities.stream()
                .sorted((a, b) -> Integer.compare(b.getQuestionCount(), a.getQuestionCount()))
                .toList();
            default -> entities; // time 已经在数据库层面排序了
        };
    }

    // ========== 统计功能 ==========

    /**
     * 获取当前用户的知识库统计信息
     * 总提问次数从用户消息数统计，确保多知识库提问只算一次
     */
    public KnowledgeBaseStatsDTO getStatistics() {
        Long userId = CurrentUser.requireId();
        return new KnowledgeBaseStatsDTO(
            knowledgeBaseRepository.countByUserId(userId),
            ragChatMessageRepository.countByUserIdAndType(userId, MessageType.USER),
            knowledgeBaseRepository.sumAccessCountByUserId(userId),
            knowledgeBaseRepository.countByUserIdAndVectorStatus(userId, VectorStatus.COMPLETED),
            knowledgeBaseRepository.countByUserIdAndVectorStatus(userId, VectorStatus.PROCESSING)
        );
    }

    // ========== 下载功能 ==========

    /**
     * 下载知识库文件（只能下自己的）
     */
    public byte[] downloadFile(Long id) {
        KnowledgeBaseEntity entity = knowledgeBaseRepository.findByIdAndUserId(id, CurrentUser.requireId())
            .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在"));

        String storageKey = entity.getStorageKey();
        if (storageKey == null || storageKey.isBlank()) {
            throw new BusinessException(ErrorCode.STORAGE_DOWNLOAD_FAILED, "文件存储信息不存在");
        }

        log.info("下载知识库文件: id={}, filename={}", id, entity.getOriginalFilename());
        return fileStorageService.downloadFile(storageKey);
    }

    /**
     * 获取知识库文件信息（用于下载，只能取自己的）
     */
    public KnowledgeBaseEntity getEntityForDownload(Long id) {
        return knowledgeBaseRepository.findByIdAndUserId(id, CurrentUser.requireId())
            .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在"));
    }
}
