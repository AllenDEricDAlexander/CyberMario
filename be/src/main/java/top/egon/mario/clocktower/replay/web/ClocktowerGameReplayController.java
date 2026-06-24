package top.egon.mario.clocktower.replay.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.clocktower.common.web.ClocktowerReactiveSupport;
import top.egon.mario.clocktower.replay.dto.ClocktowerGameHistoryResponse;
import top.egon.mario.clocktower.replay.dto.ClocktowerGameReplayResponse;
import top.egon.mario.clocktower.replay.service.ClocktowerGameReplayService;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/games")
@Validated
public class ClocktowerGameReplayController extends ClocktowerReactiveSupport {

    private final ClocktowerGameReplayService replayService;

    @GetMapping("/{gameId}/replay")
    public Mono<ApiResponse<ClocktowerGameReplayResponse>> replay(@PathVariable Long gameId,
                                                                  @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> replayService.replay(gameId, principal));
    }

    @GetMapping("/history")
    public Mono<ApiResponse<List<ClocktowerGameHistoryResponse>>> history(
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> replayService.history(principal));
    }
}
