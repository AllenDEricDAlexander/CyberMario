package top.egon.mario.rbac.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
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
import top.egon.mario.rbac.dto.request.PermissionRequest;
import top.egon.mario.rbac.dto.request.ReplaceIdsRequest;
import top.egon.mario.rbac.dto.response.PermissionResponse;
import top.egon.mario.rbac.service.RbacPermissionService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Set;

/**
 * Button-specific management endpoints backed by unified permissions.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/buttons")
@Slf4j
@Validated
public class AdminButtonController extends ReactiveRbacSupport {

    private final RbacPermissionService permissionService;

    @GetMapping
    public Mono<ApiResponse<List<PermissionResponse>>> list(@RequestParam @Min(1) Long menuId) {
        return blocking(() -> permissionService.getButtonsByMenuId(menuId));
    }

    @PostMapping
    public Mono<ApiResponse<PermissionResponse>> create(@Valid @RequestBody PermissionRequest request,
                                                        @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> permissionService.createPermission(request, actorId(principal)));
    }

    @PutMapping("/{id}")
    public Mono<ApiResponse<PermissionResponse>> update(@PathVariable @Min(1) Long id, @Valid @RequestBody PermissionRequest request,
                                                        @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> permissionService.updatePermission(id, request, actorId(principal)));
    }

    @DeleteMapping("/{id}")
    public Mono<ApiResponse<Void>> delete(@PathVariable @Min(1) Long id) {
        return blockingVoid(() -> permissionService.deletePermission(id));
    }

    @GetMapping("/{id}/apis")
    public Mono<ApiResponse<Set<Long>>> apis(@PathVariable @Min(1) Long id) {
        return blocking(() -> permissionService.getButtonApiIds(id));
    }

    @PutMapping("/{id}/apis")
    public Mono<ApiResponse<Void>> replaceApis(@PathVariable @Min(1) Long id, @Valid @RequestBody ReplaceIdsRequest request,
                                               @AuthenticationPrincipal RbacPrincipal principal) {
        return blockingVoid(() -> permissionService.replaceButtonApis(id, request.getIds(), actorId(principal)));
    }

}
