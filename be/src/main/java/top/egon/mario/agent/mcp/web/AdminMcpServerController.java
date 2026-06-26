package top.egon.mario.agent.mcp.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.agent.mcp.dto.request.CreateMcpServerRequest;
import top.egon.mario.agent.mcp.dto.request.UpdateMcpServerRequest;
import top.egon.mario.agent.mcp.dto.response.McpConnectionTestResponse;
import top.egon.mario.agent.mcp.dto.response.McpServerResponse;
import top.egon.mario.agent.mcp.dto.response.McpToolDiscoveryResponse;
import top.egon.mario.agent.mcp.runtime.McpRuntimeRefreshCoordinator;
import top.egon.mario.agent.mcp.service.McpServerConfigService;
import top.egon.mario.agent.mcp.service.McpToolDiscoveryService;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

/**
 * Admin endpoints for managed MCP server configuration.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/agent/mcp/servers")
@Validated
public class AdminMcpServerController extends McpReactiveSupport {

    private final McpServerConfigService serverConfigService;
    private final McpToolDiscoveryService toolDiscoveryService;
    private final ObjectProvider<McpRuntimeRefreshCoordinator> refreshCoordinatorProvider;

    @GetMapping
    public Mono<ApiResponse<List<McpServerResponse>>> list() {
        return blocking(serverConfigService::list);
    }

    @PostMapping
    public Mono<ApiResponse<McpServerResponse>> create(@Valid @RequestBody CreateMcpServerRequest request,
                                                       @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> serverConfigService.create(request, actorId(principal)));
    }

    @GetMapping("/{id}")
    public Mono<ApiResponse<McpServerResponse>> detail(@PathVariable @Min(1) Long id) {
        return blocking(() -> serverConfigService.get(id));
    }

    @PutMapping("/{id}")
    public Mono<ApiResponse<McpServerResponse>> update(@PathVariable @Min(1) Long id,
                                                       @Valid @RequestBody UpdateMcpServerRequest request,
                                                       @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> {
            McpServerResponse response = serverConfigService.update(id, request, actorId(principal));
            refreshServer(id, "server_update");
            return response;
        });
    }

    @PostMapping("/{id}/enable")
    public Mono<ApiResponse<Void>> enable(@PathVariable @Min(1) Long id,
                                          @AuthenticationPrincipal RbacPrincipal principal) {
        return blockingVoid(() -> {
            serverConfigService.enable(id, actorId(principal));
            refreshServer(id, "server_enable");
        });
    }

    @PostMapping("/{id}/disable")
    public Mono<ApiResponse<Void>> disable(@PathVariable @Min(1) Long id,
                                           @AuthenticationPrincipal RbacPrincipal principal) {
        return blockingVoid(() -> {
            serverConfigService.disable(id, actorId(principal));
            disableServer(id, "server_disable");
        });
    }

    @PostMapping("/{id}/test")
    public Mono<ApiResponse<McpConnectionTestResponse>> test(@PathVariable @Min(1) Long id) {
        return blocking(() -> toolDiscoveryService.testConnection(id));
    }

    @PostMapping("/{id}/discover-tools")
    public Mono<ApiResponse<McpToolDiscoveryResponse>> discoverTools(@PathVariable @Min(1) Long id,
                                                                     @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> {
            McpToolDiscoveryResponse response = toolDiscoveryService.discover(id, actorId(principal));
            refreshServer(id, "tool_discover");
            return response;
        });
    }

    @DeleteMapping("/{id}")
    public Mono<ApiResponse<Void>> delete(@PathVariable @Min(1) Long id,
                                          @AuthenticationPrincipal RbacPrincipal principal) {
        return blockingVoid(() -> {
            serverConfigService.delete(id, actorId(principal));
            disableServer(id, "server_delete");
        });
    }

    private void refreshServer(Long id, String reason) {
        McpRuntimeRefreshCoordinator refreshCoordinator = refreshCoordinatorProvider.getIfAvailable();
        if (refreshCoordinator != null) {
            refreshCoordinator.refreshServer(id, reason);
        }
    }

    private void disableServer(Long id, String reason) {
        McpRuntimeRefreshCoordinator refreshCoordinator = refreshCoordinatorProvider.getIfAvailable();
        if (refreshCoordinator != null) {
            refreshCoordinator.disableServer(id, reason);
        }
    }

    private Long actorId(RbacPrincipal principal) {
        return principal == null ? null : principal.userId();
    }

}
