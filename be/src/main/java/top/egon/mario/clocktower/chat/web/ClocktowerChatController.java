package top.egon.mario.clocktower.chat.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatConversationResponse;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatMarkReadRequest;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatMessageResponse;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatPrivateConversationRequest;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatReadStateResponse;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatSendMessageRequest;
import top.egon.mario.clocktower.chat.service.ClocktowerChatService;
import top.egon.mario.clocktower.common.web.ClocktowerReactiveSupport;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower")
@Validated
public class ClocktowerChatController extends ClocktowerReactiveSupport {

    private final ClocktowerChatService chatService;

    @GetMapping("/rooms/{roomId}/chat/conversations")
    public Mono<ApiResponse<List<ClocktowerChatConversationResponse>>> conversations(
            @PathVariable Long roomId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> chatService.conversations(roomId, principal));
    }

    @GetMapping("/chat/conversations/{conversationId}/messages")
    public Mono<ApiResponse<PageResult<ClocktowerChatMessageResponse>>> messages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            @AuthenticationPrincipal RbacPrincipal principal) {
        PageRequest pageRequest = PageRequest.of(Math.max(page - 1, 0), Math.min(size, 200));
        return blocking(() -> pageResult(chatService.messages(conversationId, pageRequest, principal)));
    }

    @PostMapping("/chat/conversations")
    public Mono<ApiResponse<ClocktowerChatConversationResponse>> privateConversation(
            @Valid @RequestBody ClocktowerChatPrivateConversationRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> chatService.privateConversation(request, principal));
    }

    @PostMapping("/chat/conversations/{conversationId}/messages")
    public Mono<ApiResponse<ClocktowerChatMessageResponse>> sendMessage(
            @PathVariable Long conversationId,
            @Valid @RequestBody ClocktowerChatSendMessageRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> chatService.sendMessage(conversationId, request, principal));
    }

    @PostMapping("/chat/conversations/{conversationId}/read")
    public Mono<ApiResponse<ClocktowerChatReadStateResponse>> markRead(
            @PathVariable Long conversationId,
            @Valid @RequestBody ClocktowerChatMarkReadRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> chatService.markRead(conversationId, request, principal));
    }

    private <T> PageResult<T> pageResult(Page<T> page) {
        return new PageResult<>(page.getContent(), page.getNumber() + 1, page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
