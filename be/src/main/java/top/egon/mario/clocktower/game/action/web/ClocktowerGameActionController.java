package top.egon.mario.clocktower.game.action.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.clocktower.common.web.ClocktowerReactiveSupport;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionRequest;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionResponse;
import top.egon.mario.clocktower.game.action.service.ClocktowerHumanGameActionService;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/games/{gameId}")
public class ClocktowerGameActionController extends ClocktowerReactiveSupport {

    private final ClocktowerHumanGameActionService humanGameActionService;

    @PostMapping("/actions")
    public Mono<ApiResponse<ClocktowerGameActionResponse>> submit(
            @PathVariable Long gameId,
            @RequestBody ClocktowerGameActionRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> humanGameActionService.submit(gameId, request, principal));
    }
}
