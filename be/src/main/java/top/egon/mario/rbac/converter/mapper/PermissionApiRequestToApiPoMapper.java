package top.egon.mario.rbac.converter.mapper;

import io.github.linpeilie.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import top.egon.mario.rbac.converter.RbacLayerEnumMapper;
import top.egon.mario.rbac.dto.PermissionRequest;
import top.egon.mario.rbac.po.ApiPo;

/**
 * MapStruct Plus mapper from API permission detail requests to persistence objects.
 */
@Mapper(componentModel = "spring", uses = RbacLayerEnumMapper.class,
        unmappedSourcePolicy = ReportingPolicy.IGNORE, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PermissionApiRequestToApiPoMapper extends BaseMapper<PermissionRequest.Api, ApiPo> {
}
