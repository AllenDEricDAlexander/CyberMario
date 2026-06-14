package top.egon.mario.rbac.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
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
import top.egon.mario.rbac.dto.request.PermissionRequest;
import top.egon.mario.rbac.dto.request.StatusRequest;
import top.egon.mario.rbac.dto.response.MenuTreeResponse;
import top.egon.mario.rbac.dto.response.PermissionResponse;
import top.egon.mario.rbac.service.RbacPermissionService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

/**
 * Management endpoints for unified permissions.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/permissions")
@Slf4j
@Validated
public class AdminPermissionController extends ReactiveRbacSupport {

    private final RbacPermissionService permissionService;

    @GetMapping
    public Mono<ApiResponse<PageResult<PermissionResponse>>> page(@RequestParam(defaultValue = "1") @Min(1) int page,
                                                                  @RequestParam(defaultValue = "20") @Min(1) int size) {
        return blocking(() -> pageResult(permissionService.getPermissionPage(PageRequest.of(Math.max(page - 1, 0), size, Sort.by("id").descending()))));
    }

    @PostMapping
    public Mono<ApiResponse<PermissionResponse>> create(@Valid @RequestBody PermissionRequest request,
                                                        @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> permissionService.createPermission(request, actorId(principal)));
    }

    @GetMapping("/{id}")
    public Mono<ApiResponse<PermissionResponse>> detail(@PathVariable @Min(1) Long id) {
        return blocking(() -> permissionService.getPermission(id));
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

    @PatchMapping("/{id}/status")
    public Mono<ApiResponse<Void>> status(@PathVariable @Min(1) Long id, @Valid @RequestBody StatusRequest request) {
        return blockingVoid(() -> permissionService.updateStatus(id, request.getPermissionStatus()));
    }

    @GetMapping("/tree")
    public Mono<ApiResponse<List<MenuTreeResponse>>> tree() {
        return blocking(permissionService::getMenuTree);
    }

}
