package top.egon.mario.agent.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.agent.soul.dto.request.AgentSoulMdUpdateRequest;
import top.egon.mario.agent.soul.dto.response.AgentSoulMdResponse;
import top.egon.mario.agent.soul.dto.response.AgentSoulMdVersionResponse;
import top.egon.mario.agent.soul.service.AgentSoulService;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

/**
 * Current-user Agent SoulMD endpoints.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me/soul-md")
@Validated
public class AgentSoulController extends ReactiveAgentSupport {

    private final AgentSoulService soulService;

    @GetMapping
    public Mono<ApiResponse<AgentSoulMdResponse>> current(@AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> soulService.currentSoul(principal));
    }

    @PutMapping
    public Mono<ApiResponse<AgentSoulMdResponse>> update(@AuthenticationPrincipal RbacPrincipal principal,
                                                         @Valid @RequestBody AgentSoulMdUpdateRequest request) {
        return blocking(() -> soulService.updateManual(request, principal));
    }

    @GetMapping("/versions")
    public Mono<ApiResponse<List<AgentSoulMdVersionResponse>>> versions(
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> soulService.versions(principal));
    }
}
