package top.egon.mario.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import top.egon.mario.agent.service.ChatAgentService;
import top.egon.mario.pojo.request.ChatRequest;
import top.egon.mario.pojo.response.ChatResponse;

/**
 * Reactive HTTP entry point for CyberMario conversations.
 */
@RestController
@RequestMapping("/demo/chat")
public class ChatController {

    private final ChatAgentService chatAgentService;

    public ChatController(ChatAgentService chatAgentService) {
        this.chatAgentService = chatAgentService;
    }

    /**
     * Starts or continues a conversation with the configured agent.
     */
    @PostMapping(path = "/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> chat(@RequestBody Mono<ChatRequest> request) {
        return request.flatMapMany(chatRequest -> chatAgentService.chat(chatRequest.message(), chatRequest.threadId()));
    }

}
