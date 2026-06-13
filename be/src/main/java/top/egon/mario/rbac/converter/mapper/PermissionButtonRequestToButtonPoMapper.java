package top.egon.mario.rbac.converter.mapper;

import io.github.linpeilie.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import top.egon.mario.rbac.dto.request.PermissionRequest;
import top.egon.mario.rbac.po.ButtonPo;

/**
 * MapStruct Plus mapper from button permission detail requests to persistence objects.
 */
@Mapper(componentModel = "spring",
        unmappedSourcePolicy = ReportingPolicy.IGNORE, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PermissionButtonRequestToButtonPoMapper extends BaseMapper<PermissionRequest.Button, ButtonPo> {
}
