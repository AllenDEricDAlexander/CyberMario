package top.egon.mario.rbac.converter.mapper.detail;

import io.github.linpeilie.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import top.egon.mario.rbac.dto.PermissionResponse;
import top.egon.mario.rbac.po.MenuPo;

/**
 * MapStruct Plus mapper from menu persistence details to response details.
 */
@Mapper(componentModel = "spring",
        unmappedSourcePolicy = ReportingPolicy.IGNORE, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MenuPoToMenuDetailMapper extends BaseMapper<MenuPo, PermissionResponse.MenuDetail> {
}
