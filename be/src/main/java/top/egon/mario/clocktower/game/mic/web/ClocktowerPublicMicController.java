package top.egon.mario.clocktower.game.mic.web;

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
import top.egon.mario.clocktower.game.mic.dto.ClocktowerMicExtendRequest;
import top.egon.mario.clocktower.game.mic.dto.ClocktowerMicSessionView;
import top.egon.mario.clocktower.game.mic.service.ClocktowerPublicMicService;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/games/{gameId}/mic")
public class ClocktowerPublicMicController extends ClocktowerReactiveSupport {

    private final ClocktowerPublicMicService micService;

    @GetMapping
    public Mono<ApiResponse<ClocktowerMicSessionView>> getMicSession(
            @PathVariable Long gameId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> micService.getMicSession(gameId, principal));
    }

    @PostMapping("/start-day")
    public Mono<ApiResponse<ClocktowerMicSessionView>> startDay(
            @PathVariable Long gameId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> micService.startDayMicSession(gameId, principal));
    }

    @PostMapping("/turns/{turnId}/finish")
    public Mono<ApiResponse<ClocktowerMicSessionView>> finish(
            @PathVariable Long gameId,
            @PathVariable Long turnId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> micService.finishCurrentTurn(gameId, turnId, principal));
    }

    @PostMapping("/turns/{turnId}/skip")
    public Mono<ApiResponse<ClocktowerMicSessionView>> skip(
            @PathVariable Long gameId,
            @PathVariable Long turnId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> micService.skipTurn(gameId, turnId, principal));
    }

    @PostMapping("/grab")
    public Mono<ApiResponse<ClocktowerMicSessionView>> grab(
            @PathVariable Long gameId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> micService.grabMic(gameId, principal));
    }

    @PostMapping("/release")
    public Mono<ApiResponse<ClocktowerMicSessionView>> release(
            @PathVariable Long gameId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> micService.releaseMic(gameId, principal));
    }

    @PostMapping("/extend")
    public Mono<ApiResponse<ClocktowerMicSessionView>> extend(
            @PathVariable Long gameId,
            @RequestBody ClocktowerMicExtendRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> micService.extendGrabMic(gameId, request.seconds(), principal));
    }

    @PostMapping("/close")
    public Mono<ApiResponse<ClocktowerMicSessionView>> close(
            @PathVariable Long gameId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> micService.closeSession(gameId, principal));
    }
}
