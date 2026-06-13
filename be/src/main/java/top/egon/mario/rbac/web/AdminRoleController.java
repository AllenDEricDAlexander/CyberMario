package top.egon.mario.rbac.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
import top.egon.mario.rbac.dto.CreateRoleRequest;
import top.egon.mario.rbac.dto.ReplaceIdsRequest;
import top.egon.mario.rbac.dto.RoleResponse;
import top.egon.mario.rbac.dto.UpdateRoleRequest;
import top.egon.mario.rbac.service.RbacRoleService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.Set;

/**
 * Management endpoints for RBAC roles.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/roles")
public class AdminRoleController extends ReactiveRbacSupport {

    private final RbacRoleService roleService;

    @GetMapping
    public Mono<ApiResponse<PageResult<RoleResponse>>> page(@RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return blocking(() -> pageResult(roleService.getRolePage(PageRequest.of(Math.max(page - 1, 0), size, Sort.by("sortNo").ascending()))));
    }

    @PostMapping
    public Mono<ApiResponse<RoleResponse>> create(@Valid @RequestBody CreateRoleRequest request) {
        return blocking(() -> roleService.createRole(request));
    }

    @GetMapping("/{id}")
    public Mono<ApiResponse<RoleResponse>> detail(@PathVariable Long id) {
        return blocking(() -> roleService.getRole(id));
    }

    @PutMapping("/{id}")
    public Mono<ApiResponse<RoleResponse>> update(@PathVariable Long id, @RequestBody UpdateRoleRequest request) {
        return blocking(() -> roleService.updateRole(id, request));
    }

    @DeleteMapping("/{id}")
    public Mono<ApiResponse<Void>> delete(@PathVariable Long id) {
        return blockingVoid(() -> roleService.deleteRole(id));
    }

    @GetMapping("/{id}/permissions")
    public Mono<ApiResponse<Set<Long>>> permissions(@PathVariable Long id) {
        return blocking(() -> roleService.getDirectPermissionIds(id));
    }

    @GetMapping("/{id}/permissions/effective")
    public Mono<ApiResponse<Set<Long>>> effectivePermissions(@PathVariable Long id) {
        return blocking(() -> roleService.getEffectivePermissionIds(id));
    }

    @PutMapping("/{id}/permissions")
    public Mono<ApiResponse<Set<Long>>> replacePermissions(@PathVariable Long id, @RequestBody ReplaceIdsRequest request,
                                                           @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> roleService.replaceRolePermissions(id, request.getIds(), request.isSyncButtonApis(), actorId(principal)));
    }

    @GetMapping("/{id}/inheritance")
    public Mono<ApiResponse<Set<Long>>> inheritance(@PathVariable Long id) {
        return blocking(() -> roleService.getInheritedRoleIds(id));
    }

    @PutMapping("/{id}/inheritance")
    public Mono<ApiResponse<Void>> replaceInheritance(@PathVariable Long id, @RequestBody ReplaceIdsRequest request,
                                                      @AuthenticationPrincipal RbacPrincipal principal) {
        return blockingVoid(() -> roleService.replaceRoleInheritance(id, request.getIds(), actorId(principal)));
    }

}
