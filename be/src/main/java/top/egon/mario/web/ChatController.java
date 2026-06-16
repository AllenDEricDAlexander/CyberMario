package top.egon.mario.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.egon.mario.agent.service.ChatAgentService;
import top.egon.mario.pojo.request.ChatRequest;
import top.egon.mario.pojo.response.ChatResponse;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;
import top.egon.mario.rbac.service.resource.annotation.RbacApi;
import top.egon.mario.rbac.service.resource.annotation.RbacMenu;
import top.egon.mario.rbac.service.resource.annotation.RbacResourceModule;
import top.egon.mario.rbac.service.security.RbacPrincipal;

/**
 * Reactive HTTP entry point for CyberMario conversations.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/demo/chat")
@Slf4j
@Validated
@RbacResourceModule(appCode = "chat", name = "Chat", codePrefixes = {"menu:chat", "api:chat:"})
@RbacMenu(code = "menu:chat", name = "Agent Chat", path = "/chat", routeName = "chat", icon = "CommentOutlined", sort = 10)
public class ChatController {

    private final ChatAgentService chatAgentService;

    /**
     * Starts or continues a conversation with the configured agent.
     */
    @RbacApi(appCode = "chat", code = "api:chat:stream", name = "Agent Chat Stream API", risk = ApiRiskLevel.MEDIUM)
    @PostMapping(path = "/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<ChatResponse> chat(@Valid @RequestBody Mono<ChatRequest> request,
                                   @AuthenticationPrincipal RbacPrincipal principal) {
        return request.flatMapMany(chatRequest -> chatAgentService.chat(chatRequest, principal));
    }

}
