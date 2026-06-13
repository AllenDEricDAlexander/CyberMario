package top.egon.mario.rbac.converter.mapper;

import io.github.linpeilie.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import top.egon.mario.rbac.converter.RbacLayerEnumMapper;
import top.egon.mario.rbac.dto.request.CreateRoleRequest;
import top.egon.mario.rbac.po.RolePo;

/**
 * MapStruct Plus mapper from role creation requests to persistence objects.
 */
@Mapper(componentModel = "spring", uses = RbacLayerEnumMapper.class,
        unmappedSourcePolicy = ReportingPolicy.IGNORE, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CreateRoleRequestToRolePoMapper extends BaseMapper<CreateRoleRequest, RolePo> {
}
