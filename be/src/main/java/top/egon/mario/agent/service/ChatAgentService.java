package top.egon.mario.agent.service;

import jakarta.validation.constraints.NotBlank;
import reactor.core.publisher.Flux;
import top.egon.mario.agent.dto.request.AgentDebugChatRequest;
import top.egon.mario.agent.externalim.model.ChatInvocation;
import top.egon.mario.pojo.request.ChatRequest;
import top.egon.mario.pojo.response.ChatResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

/**
 * Handles conversational requests for the CyberMario agent.
 */
public interface ChatAgentService {

    /**
     * Sends a user message to the agent using the provided conversation thread when present.
     * The response is emitted in multiple chunks for HTTP streaming.
     */
    Flux<ChatResponse> chat(ChatRequest request, RbacPrincipal principal);

    Flux<ChatResponse> chat(ChatInvocation invocation);

    default Flux<ChatResponse> chat(@NotBlank String message, String threadId, RbacPrincipal principal) {
        return chat(new ChatRequest(message, threadId, null, null), principal);
    }

    /**
     * Sends a user message to an agent runtime resolved from a debug preset and request overrides.
     */
    Flux<ChatResponse> debugChat(AgentDebugChatRequest request, RbacPrincipal principal);

}
