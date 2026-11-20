package interview.guide.infrastructure.mapper;

import interview.guide.common.async.dto.DeadLetterResponse;
import interview.guide.common.async.model.AsyncDeadLetterEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * 死信实体到响应对象（VO）的映射器。
 * 使用 MapStruct 自动生成转换代码；Entity 上的 payload 大字段在 VO 中不存在，
 * 通过 unmappedSourcePolicy=IGNORE 忽略（重投时由后端从 Entity 读取，不向前端暴露）。
 */
@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedSourcePolicy = ReportingPolicy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface DeadLetterMapper {

    DeadLetterResponse toResponse(AsyncDeadLetterEntity entity);

    List<DeadLetterResponse> toResponseList(List<AsyncDeadLetterEntity> entities);
}
