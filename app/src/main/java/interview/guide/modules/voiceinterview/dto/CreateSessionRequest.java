package interview.guide.modules.voiceinterview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSessionRequest {
    private String roleType; // 向后兼容，新请求可不传（服务层会用 skillId 填充）
    private String skillId;  // 模板 ID，如 "java-backend", "bytedance-backend" 等
    private String difficulty; // "junior", "mid", "senior"
    private String customJdText;
    private Long resumeId;

    // 注意：@Builder.Default 在 @NoArgsConstructor 下不会生效（Jackson 反序列化缺省字段会得到 null），
    // 因此服务层必须用 Boolean.TRUE.equals(...) 兜底，不能直接拆箱
    @Builder.Default
    private Boolean introEnabled = false;
    @Builder.Default
    private Boolean techEnabled = true;
    @Builder.Default
    private Boolean projectEnabled = true;
    @Builder.Default
    private Boolean hrEnabled = true;
    @Builder.Default
    private Integer plannedDuration = 30;

    private String llmProvider;
}
