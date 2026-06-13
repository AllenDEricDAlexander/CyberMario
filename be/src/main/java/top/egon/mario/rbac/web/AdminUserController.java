package top.egon.mario.rbac.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.rbac.dto.request.CreateUserRequest;
import top.egon.mario.rbac.dto.request.ReplaceIdsRequest;
import top.egon.mario.rbac.dto.request.ResetPasswordRequest;
import top.egon.mario.rbac.dto.request.StatusRequest;
import top.egon.mario.rbac.dto.request.UpdateUserRequest;
import top.egon.mario.rbac.dto.response.EffectivePermissionResponse;
import top.egon.mario.rbac.dto.response.UserResponse;
import top.egon.mario.rbac.po.enums.ApiMatcherType;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;
import top.egon.mario.rbac.service.RbacEffectivePermissionService;
import top.egon.mario.rbac.service.RbacUserService;
import top.egon.mario.rbac.service.resource.annotation.RbacApi;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.Set;

/**
 * Management endpoints for RBAC users.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
@Slf4j
public class AdminUserController extends ReactiveRbacSupport {

    private final RbacUserService userService;
    private final RbacEffectivePermissionService effectivePermissionService;

    @RbacApi(appCode = "rbac", code = "api:rbac:admin:*", name = "RBAC Administration APIs",
            method = "ANY", pattern = "/api/admin/**", matcher = ApiMatcherType.ANT, risk = ApiRiskLevel.HIGH)
    @GetMapping
    public Mono<ApiResponse<PageResult<UserResponse>>> page(@RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return blocking(() -> pageResult(userService.getUserPage(PageRequest.of(Math.max(page - 1, 0), size, Sort.by("id").descending()))));
    }

    @PostMapping
    public Mono<ApiResponse<UserResponse>> create(@Valid @RequestBody CreateUserRequest request,
                                                  @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> userService.createUser(request, actorId(principal)));
    }

    @GetMapping("/{id}")
    public Mono<ApiResponse<UserResponse>> detail(@PathVariable Long id) {
        return blocking(() -> userService.getUser(id));
    }

    @PutMapping("/{id}")
    public Mono<ApiResponse<UserResponse>> update(@PathVariable Long id, @RequestBody UpdateUserRequest request) {
        return blocking(() -> userService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    public Mono<ApiResponse<Void>> delete(@PathVariable Long id, @AuthenticationPrincipal RbacPrincipal principal) {
        return blockingVoid(() -> userService.deleteUser(id, actorId(principal)));
    }

    @PatchMapping("/{id}/status")
    public Mono<ApiResponse<Void>> status(@PathVariable Long id, @RequestBody StatusRequest request) {
        return blockingVoid(() -> userService.updateStatus(id, request.getRbacStatus()));
    }

    @PutMapping("/{id}/password")
    public Mono<ApiResponse<Void>> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        return blockingVoid(() -> userService.resetPassword(id, request.password()));
    }

    @GetMapping("/{id}/roles")
    public Mono<ApiResponse<Set<Long>>> roles(@PathVariable Long id) {
        return blocking(() -> userService.getDirectRoleIds(id));
    }

    @PutMapping("/{id}/roles")
    public Mono<ApiResponse<Void>> replaceRoles(@PathVariable Long id, @RequestBody ReplaceIdsRequest request,
                                                @AuthenticationPrincipal RbacPrincipal principal) {
        return blockingVoid(() -> userService.replaceUserRoles(id, request.getIds(), actorId(principal)));
    }

    @GetMapping("/{id}/permissions/effective")
    public Mono<ApiResponse<EffectivePermissionResponse>> effectivePermissions(@PathVariable Long id) {
        return blocking(() -> effectivePermissionService.getUserEffectivePermissions(id));
    }

}
