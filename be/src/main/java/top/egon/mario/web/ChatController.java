package top.egon.mario.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.egon.mario.agent.service.ChatAgentService;
import top.egon.mario.pojo.request.ChatRequest;
import top.egon.mario.pojo.response.ChatResponse;

/**
 * Reactive HTTP entry point for CyberMario conversations.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/demo/chat")
public class ChatController {

    private final ChatAgentService chatAgentService;

    /**
     * Starts or continues a conversation with the configured agent.
     */
    @PostMapping(path = "/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<ChatResponse> chat(@RequestBody Mono<ChatRequest> request) {
        return request.flatMapMany(chatRequest -> chatAgentService.chat(chatRequest.message(), chatRequest.threadId()));
    }

}
