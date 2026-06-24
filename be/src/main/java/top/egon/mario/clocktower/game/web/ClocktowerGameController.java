package top.egon.mario.clocktower.game.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.clocktower.common.web.ClocktowerReactiveSupport;
import top.egon.mario.clocktower.game.dto.ClocktowerGameResponse;
import top.egon.mario.clocktower.game.dto.ClocktowerGameTimeoutAbortRequest;
import top.egon.mario.clocktower.game.service.ClocktowerGameLifecycleService;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Duration;
import java.time.Instant;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower")
@Validated
public class ClocktowerGameController extends ClocktowerReactiveSupport {

    private static final long DEFAULT_TIMEOUT_SECONDS = 300L;

    private final ClocktowerGameLifecycleService gameLifecycleService;

    @PostMapping("/rooms/{roomId}/games/start")
    public Mono<ApiResponse<ClocktowerGameResponse>> start(@PathVariable Long roomId,
                                                           @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> gameLifecycleService.startGame(roomId, principal));
    }

    @PostMapping("/games/{gameId}/end")
    public Mono<ApiResponse<ClocktowerGameResponse>> end(@PathVariable Long gameId,
                                                         @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> gameLifecycleService.endGame(gameId, principal));
    }

    @PostMapping("/games/{gameId}/abort")
    public Mono<ApiResponse<ClocktowerGameResponse>> abort(@PathVariable Long gameId,
                                                           @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> gameLifecycleService.abortGame(gameId, principal));
    }

    @PostMapping("/rooms/{roomId}/games/timeout-abort")
    public Mono<ApiResponse<Boolean>> abortTimedOutRoom(
            @PathVariable Long roomId,
            @Valid @RequestBody(required = false) ClocktowerGameTimeoutAbortRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        long timeoutSeconds = request == null || request.timeoutSeconds() == null
                ? DEFAULT_TIMEOUT_SECONDS : request.timeoutSeconds();
        return blocking(() -> gameLifecycleService.abortTimedOutRoom(
                roomId, Duration.ofSeconds(timeoutSeconds), Instant.now(), principal));
    }
}
