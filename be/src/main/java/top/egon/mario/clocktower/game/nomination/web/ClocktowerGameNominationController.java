package top.egon.mario.clocktower.game.nomination.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.clocktower.common.web.ClocktowerReactiveSupport;
import top.egon.mario.clocktower.game.nomination.dto.ClocktowerGameExecutionResolveRequest;
import top.egon.mario.clocktower.game.nomination.dto.ClocktowerGameExecutionResponse;
import top.egon.mario.clocktower.game.nomination.dto.ClocktowerGameNominationResponse;
import top.egon.mario.clocktower.game.nomination.service.ClocktowerGameExecutionService;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/games/{gameId}")
public class ClocktowerGameNominationController extends ClocktowerReactiveSupport {

    private final ClocktowerGameExecutionService executionService;

    @PostMapping("/nominations/{nominationId}/close")
    public Mono<ApiResponse<ClocktowerGameNominationResponse>> closeNomination(
            @PathVariable Long gameId,
            @PathVariable Long nominationId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> executionService.closeNomination(gameId, nominationId, principal));
    }

    @PostMapping("/executions/resolve")
    public Mono<ApiResponse<ClocktowerGameExecutionResponse>> resolveExecution(
            @PathVariable Long gameId,
            @RequestBody ClocktowerGameExecutionResolveRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> executionService.resolveExecution(gameId, request, principal));
    }
}
