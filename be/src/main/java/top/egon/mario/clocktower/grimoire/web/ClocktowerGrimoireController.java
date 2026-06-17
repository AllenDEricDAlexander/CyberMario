package top.egon.mario.clocktower.grimoire.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.clocktower.grimoire.dto.request.StorytellerActionRequest;
import top.egon.mario.clocktower.grimoire.dto.response.ClocktowerGrimoireResponse;
import top.egon.mario.clocktower.grimoire.dto.response.NightChecklistResponse;
import top.egon.mario.clocktower.grimoire.dto.response.StorytellerActionResponse;
import top.egon.mario.clocktower.grimoire.service.ClocktowerGrimoireService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/rooms/{roomId}")
@Validated
public class ClocktowerGrimoireController {

    private final ClocktowerGrimoireService grimoireService;

    @GetMapping("/grimoire")
    public Mono<ClocktowerGrimoireResponse> grimoire(@PathVariable Long roomId,
                                                     @AuthenticationPrincipal RbacPrincipal principal) {
        return Mono.fromSupplier(() -> grimoireService.getGrimoire(roomId, principal));
    }

    @GetMapping("/night-checklist")
    public Mono<NightChecklistResponse> nightChecklist(@PathVariable Long roomId,
                                                       @AuthenticationPrincipal RbacPrincipal principal) {
        return Mono.fromSupplier(() -> grimoireService.nightChecklist(roomId, principal));
    }

    @PostMapping("/storyteller/actions")
    public Mono<StorytellerActionResponse> storytellerAction(@PathVariable Long roomId,
                                                             @RequestBody StorytellerActionRequest request,
                                                             @AuthenticationPrincipal RbacPrincipal principal) {
        return Mono.fromSupplier(() -> grimoireService.storytellerAction(roomId, request, principal));
    }
}
