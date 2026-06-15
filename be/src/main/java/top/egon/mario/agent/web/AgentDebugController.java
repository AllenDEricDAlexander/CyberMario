package top.egon.mario.agent.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.egon.mario.agent.dto.request.AgentDebugChatRequest;
import top.egon.mario.agent.service.ChatAgentService;
import top.egon.mario.pojo.response.ChatResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

/**
 * Reactive HTTP endpoints for the Agent debug workspace.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent/debug")
@Validated
public class AgentDebugController {

    private final ChatAgentService chatAgentService;

    /**
     * Starts a debug chat turn with saved preset and request-level overrides.
     */
    @PostMapping(path = "/chat/stream", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<ChatResponse> chat(@Valid @RequestBody Mono<AgentDebugChatRequest> request,
                                   @AuthenticationPrincipal RbacPrincipal principal) {
        return request.flatMapMany(chatRequest -> chatAgentService.debugChat(chatRequest, principal));
    }

}
