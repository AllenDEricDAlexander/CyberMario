package top.egon.mario.agent.service;

import jakarta.validation.constraints.NotBlank;
import reactor.core.publisher.Flux;
import top.egon.mario.pojo.response.ChatResponse;

/**
 * Handles conversational requests for the CyberMario agent.
 */
public interface ChatAgentService {

    /**
     * Sends a user message to the agent using the provided conversation thread when present.
     * The response is emitted in multiple chunks for HTTP streaming.
     */
    Flux<ChatResponse> chat(@NotBlank String message, String threadId);

}
