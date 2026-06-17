package top.egon.mario.clocktower.view.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.clocktower.view.dto.ClocktowerPlayerViewResponse;
import top.egon.mario.clocktower.view.service.ClocktowerViewService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/rooms/{roomId}")
@Validated
public class ClocktowerViewController {

    private final ClocktowerViewService viewService;

    @GetMapping("/view")
    public Mono<ClocktowerPlayerViewResponse> playerView(@PathVariable Long roomId,
                                                         @RequestParam(required = false) Long seatId,
                                                         @AuthenticationPrincipal RbacPrincipal principal) {
        return Mono.fromSupplier(() -> viewService.playerView(roomId, seatId, principal));
    }
}
