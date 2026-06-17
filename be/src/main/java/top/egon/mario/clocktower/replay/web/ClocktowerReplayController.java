package top.egon.mario.clocktower.replay.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.clocktower.common.web.ClocktowerReactiveSupport;
import top.egon.mario.clocktower.replay.dto.ClocktowerReplayResponse;
import top.egon.mario.clocktower.replay.dto.ClocktowerVoteReplayResponse;
import top.egon.mario.clocktower.replay.service.ClocktowerReplayService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/replays/{roomId}")
@Validated
public class ClocktowerReplayController extends ClocktowerReactiveSupport {

    private final ClocktowerReplayService replayService;

    @GetMapping
    public Mono<ClocktowerReplayResponse> replay(@PathVariable Long roomId,
                                                 @RequestParam(defaultValue = "PUBLIC") String mode,
                                                 @RequestParam(required = false) Long fromSeq,
                                                 @RequestParam(required = false) Long toSeq,
                                                 @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> replayService.replay(roomId, mode, fromSeq, toSeq, principal));
    }

    @GetMapping("/votes")
    public Mono<List<ClocktowerVoteReplayResponse>> votes(@PathVariable Long roomId,
                                                          @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> replayService.votes(roomId, principal));
    }
}
