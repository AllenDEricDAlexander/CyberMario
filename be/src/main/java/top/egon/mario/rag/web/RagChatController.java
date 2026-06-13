package top.egon.mario.rag.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.egon.mario.rag.dto.request.RagChatRequest;
import top.egon.mario.rag.dto.response.RagStreamEvent;
import top.egon.mario.rag.service.RagChatService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

/**
 * HTTP-streamed RAG chat endpoint.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rag/chat")
public class RagChatController {

    private final RagChatService chatService;

    @PostMapping(path = "/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<RagStreamEvent> stream(@Valid @RequestBody Mono<RagChatRequest> request,
                                       @AuthenticationPrincipal RbacPrincipal principal) {
        return request.flatMapMany(chatRequest -> chatService.stream(chatRequest, principal));
    }

}
