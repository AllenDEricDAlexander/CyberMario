package top.egon.mario.clocktower.action.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.clocktower.action.dto.ClocktowerActionRequest;
import top.egon.mario.clocktower.action.dto.ClocktowerActionResponse;
import top.egon.mario.clocktower.action.service.ClocktowerActionService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/rooms/{roomId}")
@Validated
public class ClocktowerActionController {

    private final ClocktowerActionService actionService;

    @PostMapping("/actions")
    public Mono<ClocktowerActionResponse> submit(@PathVariable Long roomId,
                                                 @RequestBody ClocktowerActionRequest request,
                                                 @AuthenticationPrincipal RbacPrincipal principal) {
        return Mono.fromSupplier(() -> actionService.submit(roomId, request, principal));
    }
}
