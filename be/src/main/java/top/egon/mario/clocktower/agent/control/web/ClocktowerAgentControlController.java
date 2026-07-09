package top.egon.mario.clocktower.agent.control.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.clocktower.agent.control.dto.ClocktowerAgentConsoleView;
import top.egon.mario.clocktower.agent.control.dto.ClocktowerAgentMemoryView;
import top.egon.mario.clocktower.agent.control.dto.ClocktowerAgentTaskView;
import top.egon.mario.clocktower.agent.control.service.ClocktowerAgentControlService;
import top.egon.mario.clocktower.common.web.ClocktowerReactiveSupport;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/games/{gameId}/agents")
public class ClocktowerAgentControlController extends ClocktowerReactiveSupport {

    private final ClocktowerAgentControlService controlService;

    @GetMapping
    public Mono<ApiResponse<List<ClocktowerAgentConsoleView>>> listAgents(
            @PathVariable Long gameId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> controlService.listAgents(gameId, principal));
    }

    @PostMapping("/{agentInstanceId}/pause")
    public Mono<ApiResponse<ClocktowerAgentConsoleView>> pause(
            @PathVariable Long gameId,
            @PathVariable Long agentInstanceId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> controlService.pauseAgent(gameId, agentInstanceId, principal));
    }

    @PostMapping("/{agentInstanceId}/resume")
    public Mono<ApiResponse<ClocktowerAgentConsoleView>> resume(
            @PathVariable Long gameId,
            @PathVariable Long agentInstanceId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> controlService.resumeAgent(gameId, agentInstanceId, principal));
    }

    @PostMapping("/{agentInstanceId}/run-now")
    public Mono<ApiResponse<ClocktowerAgentTaskView>> runNow(
            @PathVariable Long gameId,
            @PathVariable Long agentInstanceId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> controlService.runNow(gameId, agentInstanceId, principal));
    }

    @GetMapping("/{agentInstanceId}/memory")
    public Mono<ApiResponse<List<ClocktowerAgentMemoryView>>> memory(
            @PathVariable Long gameId,
            @PathVariable Long agentInstanceId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> controlService.listMemory(gameId, agentInstanceId, principal));
    }

    @GetMapping("/{agentInstanceId}/tasks")
    public Mono<ApiResponse<List<ClocktowerAgentTaskView>>> tasks(
            @PathVariable Long gameId,
            @PathVariable Long agentInstanceId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> controlService.listTasks(gameId, agentInstanceId, principal));
    }
}
