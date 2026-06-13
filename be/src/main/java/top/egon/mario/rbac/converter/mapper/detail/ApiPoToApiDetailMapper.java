package top.egon.mario.rbac.converter.mapper.detail;

import io.github.linpeilie.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import top.egon.mario.rbac.converter.RbacLayerEnumMapper;
import top.egon.mario.rbac.dto.PermissionResponse;
import top.egon.mario.rbac.po.ApiPo;

/**
 * MapStruct Plus mapper from API persistence details to response details.
 */
@Mapper(componentModel = "spring", uses = RbacLayerEnumMapper.class,
        unmappedSourcePolicy = ReportingPolicy.IGNORE, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ApiPoToApiDetailMapper extends BaseMapper<ApiPo, PermissionResponse.ApiDetail> {
}
