package top.egon.mario.clocktower.game.flow.web;

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
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameAdvanceRequest;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameAdvanceResult;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameFlowView;
import top.egon.mario.clocktower.game.flow.service.ClocktowerGameFlowService;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/games/{gameId}/flow")
public class ClocktowerGameFlowController extends ClocktowerReactiveSupport {

    private final ClocktowerGameFlowService flowService;

    @GetMapping
    public Mono<ApiResponse<ClocktowerGameFlowView>> getFlow(
            @PathVariable Long gameId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> flowService.getFlow(gameId, principal));
    }

    @PostMapping("/advance")
    public Mono<ApiResponse<ClocktowerGameAdvanceResult>> advance(
            @PathVariable Long gameId,
            @RequestBody(required = false) ClocktowerGameAdvanceRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> flowService.advance(gameId, request, principal));
    }

    @PostMapping("/force-advance")
    public Mono<ApiResponse<ClocktowerGameAdvanceResult>> forceAdvance(
            @PathVariable Long gameId,
            @RequestBody ClocktowerGameAdvanceRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> flowService.forceAdvance(gameId, request, principal));
    }
}
