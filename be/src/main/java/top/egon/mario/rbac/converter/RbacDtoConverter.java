package top.egon.mario.rbac.converter;

import io.github.linpeilie.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.egon.mario.rbac.dto.PermissionResponse;
import top.egon.mario.rbac.dto.RoleResponse;
import top.egon.mario.rbac.dto.UserResponse;
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

    public UserResponse toUserResponse(UserPo userPo) {
        return converter.convert(userPo, UserResponse.class);
    }

    public RoleResponse toRoleResponse(RolePo rolePo) {
        return converter.convert(rolePo, RoleResponse.class);
    }

    public PermissionResponse toPermissionResponse(PermissionPo permissionPo, MenuPo menuPo, ButtonPo buttonPo,
                                                   ApiPo apiPo, Set<Long> apiPermissionIds) {
        PermissionResponse response = converter.convert(permissionPo, PermissionResponse.class);
        if (menuPo != null) {
            PermissionResponse.MenuDetail detail = new PermissionResponse.MenuDetail();
            detail.setParentMenuId(menuPo.getParentMenuId());
            detail.setRouteName(menuPo.getRouteName());
            detail.setRoutePath(menuPo.getRoutePath());
            detail.setComponent(menuPo.getComponent());
            detail.setRedirect(menuPo.getRedirect());
            detail.setIcon(menuPo.getIcon());
            detail.setHidden(menuPo.isHidden());
            detail.setCacheable(menuPo.isCacheable());
            detail.setExternalLink(menuPo.getExternalLink());
            response.setMenu(detail);
        }
        if (buttonPo != null) {
            PermissionResponse.ButtonDetail detail = new PermissionResponse.ButtonDetail();
            detail.setMenuPermissionId(buttonPo.getMenuPermissionId());
            detail.setButtonKey(buttonPo.getButtonKey());
            detail.setFrontendAction(buttonPo.getFrontendAction());
            detail.setStyleHint(buttonPo.getStyleHint());
            detail.setDescription(buttonPo.getDescription());
            detail.setApiPermissionIds(apiPermissionIds);
            response.setButton(detail);
        }
        if (apiPo != null) {
            PermissionResponse.ApiDetail detail = new PermissionResponse.ApiDetail();
            detail.setHttpMethod(apiPo.getHttpMethod());
            detail.setUrlPattern(apiPo.getUrlPattern());
            detail.setMatcherType(apiPo.getMatcherType().name());
            detail.setPublicFlag(apiPo.isPublicFlag());
            detail.setServiceTag(apiPo.getServiceTag());
            detail.setOperationName(apiPo.getOperationName());
            detail.setRiskLevel(apiPo.getRiskLevel().name());
            response.setApi(detail);
        }
        return response;
    }

}
