package top.egon.mario.rbac.converter.mapper.detail;

import io.github.linpeilie.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import top.egon.mario.rbac.dto.PermissionResponse;
import top.egon.mario.rbac.po.ButtonPo;

/**
 * MapStruct Plus mapper from button persistence details to response details.
 */
@Mapper(componentModel = "spring",
        unmappedSourcePolicy = ReportingPolicy.IGNORE, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ButtonPoToButtonDetailMapper extends BaseMapper<ButtonPo, PermissionResponse.ButtonDetail> {
}
