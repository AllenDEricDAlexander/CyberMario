package top.egon.mario.clocktower.view.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.clocktower.common.web.ClocktowerReactiveSupport;
import top.egon.mario.clocktower.view.dto.ClocktowerGameViewResponse;
import top.egon.mario.clocktower.view.service.ClocktowerGameViewService;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/games/{gameId}")
@Validated
public class ClocktowerGameViewController extends ClocktowerReactiveSupport {

    private final ClocktowerGameViewService gameViewService;

    @GetMapping("/view")
    public Mono<ApiResponse<ClocktowerGameViewResponse>> gameView(@PathVariable Long gameId,
                                                                  @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> gameViewService.gameView(gameId, principal));
    }
}
