package top.egon.mario.clocktower.game.night.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.clocktower.common.web.ClocktowerReactiveSupport;
import top.egon.mario.clocktower.game.night.dto.ClocktowerNightResolveRequest;
import top.egon.mario.clocktower.game.night.dto.ClocktowerNightSkipRequest;
import top.egon.mario.clocktower.game.night.dto.ClocktowerNightTaskView;
import top.egon.mario.clocktower.game.night.service.ClocktowerGameNightTaskService;
import top.egon.mario.clocktower.game.night.service.ClocktowerNightResolutionService;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/games/{gameId}/night-tasks")
public class ClocktowerGameNightTaskController extends ClocktowerReactiveSupport {

    private final ClocktowerGameNightTaskService nightTaskService;
    private final ClocktowerNightResolutionService resolutionService;

    @GetMapping
    public Mono<ApiResponse<List<ClocktowerNightTaskView>>> currentTasks(
            @PathVariable Long gameId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> nightTaskService.currentTasks(gameId, principal));
    }

    @PostMapping("/{taskId}/skip")
    public Mono<ApiResponse<ClocktowerNightTaskView>> skipTask(
            @PathVariable Long gameId,
            @PathVariable Long taskId,
            @RequestBody(required = false) ClocktowerNightSkipRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> nightTaskService.skipTask(gameId, taskId, request, principal));
    }

    @PostMapping("/{taskId}/resolve")
    public Mono<ApiResponse<ClocktowerNightTaskView>> resolveTask(
            @PathVariable Long gameId,
            @PathVariable Long taskId,
            @RequestBody(required = false) ClocktowerNightResolveRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> resolutionService.resolveTask(gameId, taskId, request, principal));
    }

    @PostMapping("/resolve-ready")
    public Mono<ApiResponse<List<ClocktowerNightTaskView>>> resolveReady(
            @PathVariable Long gameId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> resolutionService.resolveReady(gameId, principal));
    }
}
