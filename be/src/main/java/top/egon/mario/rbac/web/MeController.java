package top.egon.mario.rbac.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.rbac.dto.request.ChangeCurrentUserPasswordRequest;
import top.egon.mario.rbac.dto.request.UpdateCurrentUserProfileRequest;
import top.egon.mario.rbac.dto.response.EffectivePermissionResponse;
import top.egon.mario.rbac.dto.response.MenuTreeResponse;
import top.egon.mario.rbac.dto.response.UserResponse;
import top.egon.mario.rbac.po.enums.ApiMatcherType;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;
import top.egon.mario.rbac.service.RbacEffectivePermissionService;
import top.egon.mario.rbac.service.RbacUserService;
import top.egon.mario.rbac.service.resource.annotation.RbacApi;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Set;

/**
 * Current-user authorization endpoints for menus, buttons and permissions.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me")
@Slf4j
@Validated
public class MeController extends ReactiveRbacSupport {

    private final RbacEffectivePermissionService effectivePermissionService;
    private final RbacUserService userService;

    @RbacApi(appCode = "rbac", code = "api:rbac:me:self", name = "RBAC 当前用户自助接口",
            method = "ANY", pattern = "/api/me/**", matcher = ApiMatcherType.ANT, risk = ApiRiskLevel.MEDIUM)
    @PutMapping("/profile")
    public Mono<ApiResponse<UserResponse>> updateProfile(@AuthenticationPrincipal RbacPrincipal principal,
                                                         @Valid @RequestBody UpdateCurrentUserProfileRequest request) {
        return blocking(() -> userService.updateCurrentUserProfile(principal.userId(), request));
    }

    @PutMapping("/password")
    public Mono<ApiResponse<Void>> changePassword(@AuthenticationPrincipal RbacPrincipal principal,
                                                  @Valid @RequestBody ChangeCurrentUserPasswordRequest request) {
        return blockingVoid(() -> userService.changeCurrentUserPassword(principal.userId(), request));
    }

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
