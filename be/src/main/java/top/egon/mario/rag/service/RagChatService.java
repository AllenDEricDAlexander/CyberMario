package top.egon.mario.rag.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Flux;
import top.egon.mario.rag.dto.request.RagChatRequest;
import top.egon.mario.rag.dto.response.RagStreamEvent;
import top.egon.mario.rbac.service.security.RbacPrincipal;

/**
 * Application service for HTTP-streamed RAG chat.
 */
public interface RagChatService {

    /**
     * Answers a user question with streamed RAG events.
     */
    Flux<RagStreamEvent> stream(@Valid @NotNull RagChatRequest request, RbacPrincipal principal);

}
