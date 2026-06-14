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
import top.egon.mario.rbac.dto.response.PermissionResponse;
import top.egon.mario.rbac.service.RbacException;
import top.egon.mario.rbac.service.RbacPermissionService;
import top.egon.mario.rbac.service.model.ApiPermissionRule;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

/**
 * API-permission management endpoints for manually maintained API rules.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/api-permissions")
@Slf4j
@Validated
public class AdminApiPermissionController extends ReactiveRbacSupport {

    private final RbacPermissionService permissionService;

    @GetMapping
    public Mono<ApiResponse<PageResult<PermissionResponse>>> page(@RequestParam(defaultValue = "1") @Min(1) int page,
                                                                  @RequestParam(defaultValue = "20") @Min(1) int size) {
        return blocking(() -> pageResult(permissionService.getApiPermissionPage(PageRequest.of(Math.max(page - 1, 0), size, Sort.by("id").descending()))));
    }

    @GetMapping("/rules")
    public Mono<ApiResponse<List<ApiPermissionRule>>> listRules() {
        return blocking(permissionService::findEnabledApiRules);
    }

    @GetMapping("/{id}")
    public Mono<ApiResponse<PermissionResponse>> detail(@PathVariable @Min(1) Long id) {
        return blocking(() -> permissionService.getPermission(id));
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

    @PostMapping("/scan")
    public Mono<ApiResponse<Void>> scan() {
        return Mono.error(new RbacException("RBAC_API_SCAN_NOT_SUPPORTED", "API scan is not included in rbac1 first release"));
    }

    @GetMapping("/unbound")
    public Mono<ApiResponse<List<ApiPermissionRule>>> unbound() {
        return blocking(permissionService::findEnabledApiRules);
    }

}
