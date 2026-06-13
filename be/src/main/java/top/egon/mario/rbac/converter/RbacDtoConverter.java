package top.egon.mario.rbac.converter;

import io.github.linpeilie.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.egon.mario.rbac.dto.request.CreateRoleRequest;
import top.egon.mario.rbac.dto.request.CreateUserRequest;
import top.egon.mario.rbac.dto.request.PermissionRequest;
import top.egon.mario.rbac.dto.response.PermissionResponse;
import top.egon.mario.rbac.dto.response.RoleResponse;
import top.egon.mario.rbac.dto.response.UserResponse;
import top.egon.mario.rbac.po.ApiPo;
import top.egon.mario.rbac.po.ButtonPo;
import top.egon.mario.rbac.po.MenuPo;
import top.egon.mario.rbac.po.PermissionPo;
import top.egon.mario.rbac.po.RolePo;
import top.egon.mario.rbac.po.UserPo;

import java.util.Set;

/**
 * Converts RBAC persistence objects to API DTOs.
 */
@Component
@RequiredArgsConstructor
public class RbacDtoConverter {

    private final Converter converter;
    private final RbacLayerEnumMapper enumMapper;

    public UserResponse toUserResponse(UserPo userPo) {
        return converter.convert(userPo, UserResponse.class);
    }

    public RoleResponse toRoleResponse(RolePo rolePo) {
        return converter.convert(rolePo, RoleResponse.class);
    }

    public UserPo toUserPo(CreateUserRequest request) {
        return converter.convert(request, UserPo.class);
    }

    public RolePo toRolePo(CreateRoleRequest request) {
        return converter.convert(request, RolePo.class);
    }

    public PermissionPo toPermissionPo(PermissionRequest request) {
        return converter.convert(request, PermissionPo.class);
    }

    public MenuPo toMenuPo(PermissionRequest.Menu request) {
        return converter.convert(request, MenuPo.class);
    }

    public ButtonPo toButtonPo(PermissionRequest.Button request) {
        return converter.convert(request, ButtonPo.class);
    }

    public ApiPo toApiPo(PermissionRequest.Api request) {
        return converter.convert(request, ApiPo.class);
    }

    public top.egon.mario.rbac.po.enums.RbacStatus toPoRbacStatus(top.egon.mario.rbac.dto.enums.RbacStatus status) {
        return enumMapper.toPo(status);
    }

    public top.egon.mario.rbac.po.enums.PermissionType toPoPermissionType(top.egon.mario.rbac.dto.enums.PermissionType permissionType) {
        return enumMapper.toPo(permissionType);
    }

    public top.egon.mario.rbac.po.enums.PermissionStatus toPoPermissionStatus(top.egon.mario.rbac.dto.enums.PermissionStatus status) {
        return enumMapper.toPo(status);
    }

    public top.egon.mario.rbac.po.enums.ApiMatcherType toPoApiMatcherType(top.egon.mario.rbac.dto.enums.ApiMatcherType matcherType) {
        return enumMapper.toPo(matcherType);
    }

    public top.egon.mario.rbac.po.enums.ApiRiskLevel toPoApiRiskLevel(top.egon.mario.rbac.dto.enums.ApiRiskLevel riskLevel) {
        return enumMapper.toPo(riskLevel);
    }

    public PermissionResponse toPermissionResponse(PermissionPo permissionPo, MenuPo menuPo, ButtonPo buttonPo,
                                                   ApiPo apiPo, Set<Long> apiPermissionIds) {
        PermissionResponse response = converter.convert(permissionPo, PermissionResponse.class);
        if (menuPo != null) {
            response.setMenu(converter.convert(menuPo, PermissionResponse.MenuDetail.class));
        }
        if (buttonPo != null) {
            PermissionResponse.ButtonDetail detail = converter.convert(buttonPo, PermissionResponse.ButtonDetail.class);
            detail.setApiPermissionIds(apiPermissionIds);
            response.setButton(detail);
        }
        if (apiPo != null) {
            response.setApi(converter.convert(apiPo, PermissionResponse.ApiDetail.class));
        }
        return response;
    }

}
