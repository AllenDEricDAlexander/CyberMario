package top.egon.mario.clocktower.chat;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatConversationResponse;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatMarkReadRequest;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatMessageResponse;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatPrivateConversationRequest;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatReadStateResponse;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatSendMessageRequest;
import top.egon.mario.clocktower.chat.service.ClocktowerChatService;
import top.egon.mario.clocktower.chat.web.ClocktowerChatController;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClocktowerChatControllerTests {

    private final ClocktowerChatService chatService = mock(ClocktowerChatService.class);
    private final ClocktowerChatController controller = controller(chatService);

    @Test
    void listConversationsReturnsVisibleConversations() {
        ClocktowerChatConversationResponse conversation = conversation(101L, "PUBLIC", "PUBLIC");
        when(chatService.conversations(10L, principal())).thenReturn(List.of(conversation));

        StepVerifier.create(controller.conversations(10L, principal())
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-chat-list")))
                .assertNext(response -> {
                    assertThat(response.traceId()).isEqualTo("trace-chat-list");
                    assertThat(response.data()).containsExactly(conversation);
                })
                .verifyComplete();
    }

    @Test
    void historyReturnsPagedMessages() {
        ClocktowerChatMessageResponse message = new ClocktowerChatMessageResponse(501L, 101L, 2L, 1L,
                "TEXT", "hello", Instant.parse("2026-06-24T07:00:00Z"));
        when(chatService.messages(any(), any(), any())).thenReturn(new PageImpl<>(
                List.of(message), PageRequest.of(0, 20), 1));

        StepVerifier.create(controller.messages(101L, 1, 20, principal())
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-chat-history")))
                .assertNext(response -> {
                    assertThat(response.traceId()).isEqualTo("trace-chat-history");
                    PageResult<ClocktowerChatMessageResponse> page = response.data();
                    assertThat(page.records()).containsExactly(message);
                    assertThat(page.page()).isEqualTo(1);
                    assertThat(page.size()).isEqualTo(20);
                })
                .verifyComplete();
    }

    @Test
    void createPrivateConversationDelegatesRequest() {
        ClocktowerChatPrivateConversationRequest request =
                new ClocktowerChatPrivateConversationRequest(10L, 3L);
        ClocktowerChatConversationResponse conversation = conversation(202L, "PRIVATE", "PRIVATE");
        when(chatService.privateConversation(request, principal())).thenReturn(conversation);

        StepVerifier.create(controller.privateConversation(request, principal())
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-chat-private")))
                .assertNext(response -> {
                    assertThat(response.traceId()).isEqualTo("trace-chat-private");
                    assertThat(response.data()).isEqualTo(conversation);
                })
                .verifyComplete();
    }

    @Test
    void sendMessageDelegatesRequest() {
        ClocktowerChatSendMessageRequest request = new ClocktowerChatSendMessageRequest("hello", null);
        ClocktowerChatMessageResponse message = new ClocktowerChatMessageResponse(601L, 101L, 2L, 1L,
                "TEXT", "hello", Instant.parse("2026-06-24T07:00:00Z"));
        when(chatService.sendMessage(101L, request, principal())).thenReturn(message);

        StepVerifier.create(controller.sendMessage(101L, request, principal())
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-chat-send")))
                .assertNext(response -> {
                    assertThat(response.traceId()).isEqualTo("trace-chat-send");
                    assertThat(response.data()).isEqualTo(message);
                })
                .verifyComplete();
    }

    @Test
    void markReadDelegatesRequest() {
        ClocktowerChatMarkReadRequest request = new ClocktowerChatMarkReadRequest(3L);
        ClocktowerChatReadStateResponse readState =
                new ClocktowerChatReadStateResponse(701L, 101L, 2L, 3L, Instant.parse("2026-06-24T07:00:00Z"));
        when(chatService.markRead(101L, request, principal())).thenReturn(readState);

        StepVerifier.create(controller.markRead(101L, request, principal())
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-chat-read")))
                .assertNext(response -> {
                    assertThat(response.traceId()).isEqualTo("trace-chat-read");
                    assertThat(response.data()).isEqualTo(readState);
                })
                .verifyComplete();

        ArgumentCaptor<ClocktowerChatMarkReadRequest> requestCaptor =
                ArgumentCaptor.forClass(ClocktowerChatMarkReadRequest.class);
        verify(chatService).markRead(eq(101L), requestCaptor.capture(), any());
        assertThat(requestCaptor.getValue().messageSeq()).isEqualTo(3L);
    }

    private static ClocktowerChatController controller(ClocktowerChatService service) {
        ClocktowerChatController controller = new ClocktowerChatController(service);
        ReflectionTestUtils.invokeMethod(controller, "setBlockingScheduler", Schedulers.immediate());
        return controller;
    }

    private static ClocktowerChatConversationResponse conversation(Long conversationId, String groupKey,
                                                                   String conversationType) {
        return new ClocktowerChatConversationResponse(conversationId, 10L, 20L, "GAME", groupKey,
                conversationType, "GAME:20", 0L, null);
    }

    private static RbacPrincipal principal() {
        return new RbacPrincipal(2L, "luigi", Set.of("CLOCKTOWER_PLAYER"), Set.of(), "v1");
    }
}
