package top.egon.mario.rbac.converter.mapper;

import io.github.linpeilie.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import top.egon.mario.rbac.converter.RbacLayerEnumMapper;
import top.egon.mario.rbac.dto.request.PermissionRequest;
import top.egon.mario.rbac.po.PermissionPo;

/**
 * MapStruct Plus mapper from permission write requests to persistence objects.
 */
@Mapper(componentModel = "spring", uses = RbacLayerEnumMapper.class,
        unmappedSourcePolicy = ReportingPolicy.IGNORE, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PermissionRequestToPermissionPoMapper extends BaseMapper<PermissionRequest, PermissionPo> {
}
