package top.egon.mario.rbac.converter.mapper;

import io.github.linpeilie.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import top.egon.mario.rbac.dto.PermissionRequest;
import top.egon.mario.rbac.po.MenuPo;

/**
 * MapStruct Plus mapper from menu permission detail requests to persistence objects.
 */
@Mapper(componentModel = "spring",
        unmappedSourcePolicy = ReportingPolicy.IGNORE, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PermissionMenuRequestToMenuPoMapper extends BaseMapper<PermissionRequest.Menu, MenuPo> {
}
