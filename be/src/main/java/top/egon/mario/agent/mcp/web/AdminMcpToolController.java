package top.egon.mario.agent.mcp.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.agent.mcp.dto.request.UpdateMcpToolPolicyRequest;
import top.egon.mario.agent.mcp.dto.response.McpToolResponse;
import top.egon.mario.agent.mcp.runtime.McpRuntimeRefreshCoordinator;
import top.egon.mario.agent.mcp.service.McpToolConfigService;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

/**
 * Admin endpoints for discovered MCP tool policies.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/agent/mcp/tools")
@Validated
public class AdminMcpToolController extends McpReactiveSupport {

    private final McpToolConfigService toolConfigService;
    private final ObjectProvider<McpRuntimeRefreshCoordinator> refreshCoordinatorProvider;

    @GetMapping
    public Mono<ApiResponse<List<McpToolResponse>>> list(@RequestParam(required = false) @Min(1) Long serverId) {
        return blocking(() -> toolConfigService.list(serverId));
    }

    @GetMapping("/{id}")
    public Mono<ApiResponse<McpToolResponse>> detail(@PathVariable @Min(1) Long id) {
        return blocking(() -> toolConfigService.get(id));
    }

    @PutMapping("/{id}/policy")
    public Mono<ApiResponse<McpToolResponse>> updatePolicy(@PathVariable @Min(1) Long id,
                                                           @Valid @RequestBody UpdateMcpToolPolicyRequest request,
                                                           @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> {
            McpToolResponse response = toolConfigService.updatePolicy(id, request, actorId(principal));
            refreshServer(response.serverId(), "tool_policy_update");
            return response;
        });
    }

    @PostMapping("/{id}/enable")
    public Mono<ApiResponse<Void>> enable(@PathVariable @Min(1) Long id,
                                          @AuthenticationPrincipal RbacPrincipal principal) {
        return blockingVoid(() -> {
            McpToolResponse response = toolConfigService.enable(id, actorId(principal));
            refreshServer(response.serverId(), "tool_enable");
        });
    }

    @PostMapping("/{id}/disable")
    public Mono<ApiResponse<Void>> disable(@PathVariable @Min(1) Long id,
                                           @AuthenticationPrincipal RbacPrincipal principal) {
        return blockingVoid(() -> {
            McpToolResponse response = toolConfigService.disable(id, actorId(principal));
            refreshServer(response.serverId(), "tool_disable");
        });
    }

    private void refreshServer(Long id, String reason) {
        McpRuntimeRefreshCoordinator refreshCoordinator = refreshCoordinatorProvider.getIfAvailable();
        if (refreshCoordinator != null) {
            refreshCoordinator.refreshServer(id, reason);
        }
    }

    private Long actorId(RbacPrincipal principal) {
        return principal == null ? null : principal.userId();
    }

}
