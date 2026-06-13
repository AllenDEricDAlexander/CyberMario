package top.egon.mario.rbac.converter.mapper;

import io.github.linpeilie.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import top.egon.mario.rbac.converter.RbacLayerEnumMapper;
import top.egon.mario.rbac.dto.RoleResponse;
import top.egon.mario.rbac.po.RolePo;

/**
 * MapStruct Plus mapper from role persistence objects to API responses.
 */
@Mapper(componentModel = "spring", uses = RbacLayerEnumMapper.class,
        unmappedSourcePolicy = ReportingPolicy.IGNORE, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RolePoToRoleResponseMapper extends BaseMapper<RolePo, RoleResponse> {
}
