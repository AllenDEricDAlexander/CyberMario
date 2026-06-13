package top.egon.mario.rbac.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.rbac.dto.EffectivePermissionResponse;
import top.egon.mario.rbac.dto.MenuTreeResponse;
import top.egon.mario.rbac.service.RbacEffectivePermissionService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Set;

/**
 * Current-user authorization endpoints for menus, buttons and permissions.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me")
public class MeController extends ReactiveRbacSupport {

    private final RbacEffectivePermissionService effectivePermissionService;

    @GetMapping("/menus")
    public Mono<ApiResponse<List<MenuTreeResponse>>> menus(@AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> effectivePermissionService.getUserMenuTree(principal.userId()));
    }

    @GetMapping("/buttons")
    public Mono<ApiResponse<Set<String>>> buttons(@AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> effectivePermissionService.getUserEffectivePermissions(principal.userId()).buttonCodes());
    }

    @GetMapping("/permissions")
    public Mono<ApiResponse<EffectivePermissionResponse>> permissions(@AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> effectivePermissionService.getUserEffectivePermissions(principal.userId()));
    }

}
