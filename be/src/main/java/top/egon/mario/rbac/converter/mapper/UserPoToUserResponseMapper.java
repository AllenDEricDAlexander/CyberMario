package top.egon.mario.rbac.converter.mapper;

import io.github.linpeilie.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import top.egon.mario.rbac.converter.RbacLayerEnumMapper;
import top.egon.mario.rbac.dto.response.UserResponse;
import top.egon.mario.rbac.po.UserPo;

/**
 * MapStruct Plus mapper from user persistence objects to API responses.
 */
@Mapper(componentModel = "spring", uses = RbacLayerEnumMapper.class,
        unmappedSourcePolicy = ReportingPolicy.IGNORE, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserPoToUserResponseMapper extends BaseMapper<UserPo, UserResponse> {
}
