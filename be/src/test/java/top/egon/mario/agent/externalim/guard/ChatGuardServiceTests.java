package top.egon.mario.agent.externalim.guard;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.egon.mario.agent.externalim.guard.impl.DefaultChatGuardService;
import top.egon.mario.agent.externalim.model.ChatInvocation;
import top.egon.mario.agent.externalim.model.ChatSource;
import top.egon.mario.agent.externalim.model.ExternalChatPlatform;
import top.egon.mario.agent.externalim.model.ExternalConversationType;
import top.egon.mario.agent.externalim.model.ExternalMessageType;
import top.egon.mario.agent.externalim.model.ExternalSender;
import top.egon.mario.agent.externalim.model.ExternalSenderType;
import top.egon.mario.agent.model.dto.enums.ModelProviderType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ChatGuardServiceTests {

    private final ChatGuardModel model = mock(ChatGuardModel.class);
    private final ChatGuardAuditService auditService = mock(ChatGuardAuditService.class);
    private ExecutorService executor;
    private DefaultChatGuardService service;

    @BeforeEach
    void setUp() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        service = service(new ChatGuardProperties(ModelProviderType.DASHSCOPE, "guard-model",
                BigDecimal.ZERO, 256, new BigDecimal("0.85"), Duration.ofSeconds(1)));
    }

    @AfterEach
    void tearDown() {
        executor.close();
    }

    @Test
    void webDirectMentionAndReplyToAgentAreHardRepliesWithoutAModelCall() {
        assertThat(service.decide(webInvocation(), "", "request-web", "trace-1").join().decision())
                .isEqualTo(ChatGuardDecision.REPLY);
        assertThat(service.decide(externalDirect(), "", "request-direct", "trace-1").join().decision())
                .isEqualTo(ChatGuardDecision.REPLY);
        assertThat(service.decide(externalGroup(true, false), "", "request-mention", "trace-1").join().decision())
                .isEqualTo(ChatGuardDecision.REPLY);
        assertThat(service.decide(externalGroup(false, true), "", "request-reply", "trace-1").join().decision())
                .isEqualTo(ChatGuardDecision.REPLY);

        verifyNoInteractions(model);
        verify(auditService, times(4)).record(any(), any(), anyString(), anyString());
    }

    @Test
    void botSystemBlankAndUnsupportedMessagesAreHardIgnored() {
        assertThat(service.decide(groupFrom(ExternalSenderType.BOT, ExternalMessageType.TEXT, "hello"),
                "", "request-1", "trace-1").join().decision()).isEqualTo(ChatGuardDecision.IGNORE);
        assertThat(service.decide(groupFrom(ExternalSenderType.SYSTEM, ExternalMessageType.TEXT, "notice"),
                "", "request-2", "trace-1").join().decision()).isEqualTo(ChatGuardDecision.IGNORE);
        assertThat(service.decide(groupFrom(ExternalSenderType.HUMAN, ExternalMessageType.TEXT, " "),
                "", "request-3", "trace-1").join().decision()).isEqualTo(ChatGuardDecision.IGNORE);
        assertThat(service.decide(groupFrom(ExternalSenderType.HUMAN, ExternalMessageType.UNSUPPORTED, ""),
                "", "request-4", "trace-1").join().decision()).isEqualTo(ChatGuardDecision.IGNORE);
        verifyNoInteractions(model);
    }

    @Test
    void ordinaryGroupRequiresThresholdAndFailsClosed() {
        given(model.evaluate(any())).willReturn(new ChatGuardResult(
                ChatGuardDecision.REPLY, new BigDecimal("0.84"), "possibly directed",
                "DASHSCOPE", "guard-model", 10L));

        ChatGuardResult low = service.decide(externalGroup(false, false), "recent group",
                "request-low", "trace-1").join();

        assertThat(low.decision()).isEqualTo(ChatGuardDecision.IGNORE);
        assertThat(low.reason()).contains("below threshold");
    }

    @Test
    void modelTimeoutReturnsIgnoreInsteadOfFailingTheChatFlow() {
        given(model.evaluate(any())).willAnswer(invocation -> {
            Thread.sleep(500);
            return ChatGuardResult.reply("late");
        });
        DefaultChatGuardService shortTimeout = service(new ChatGuardProperties(
                ModelProviderType.DASHSCOPE, "guard-model", BigDecimal.ZERO, 256,
                new BigDecimal("0.85"), Duration.ofMillis(20)));

        ChatGuardResult result = shortTimeout.decide(externalGroup(false, false), "",
                "request-timeout", "trace-1").join();

        assertThat(result.decision()).isEqualTo(ChatGuardDecision.IGNORE);
        assertThat(result.reason()).contains("timeout");
    }

    private DefaultChatGuardService service(ChatGuardProperties properties) {
        return new DefaultChatGuardService(model, auditService, properties, executor);
    }

    private ChatInvocation webInvocation() {
        return ChatInvocation.web("hello", 8L, "luigi", "session-1", null);
    }

    private ChatInvocation externalDirect() {
        return invocation(ExternalConversationType.DIRECT, ExternalSenderType.HUMAN,
                ExternalMessageType.TEXT, "hello", false, false);
    }

    private ChatInvocation externalGroup(boolean mentioned, boolean replied) {
        return invocation(ExternalConversationType.GROUP, ExternalSenderType.HUMAN,
                ExternalMessageType.TEXT, "hello", mentioned, replied);
    }

    private ChatInvocation groupFrom(ExternalSenderType senderType, ExternalMessageType messageType,
                                     String message) {
        return invocation(ExternalConversationType.GROUP, senderType, messageType,
                message, false, false);
    }

    private ChatInvocation invocation(ExternalConversationType conversationType,
                                      ExternalSenderType senderType,
                                      ExternalMessageType messageType,
                                      String message, boolean mentioned, boolean replied) {
        return new ChatInvocation(ChatSource.EXTERNAL_IM, message, 8L, null, null, "space-1",
                ExternalChatPlatform.TELEGRAM, "main", "-1001", conversationType,
                "telegram:main:-1001", new ExternalSender("42", "Alice", senderType),
                messageType, mentioned, replied, "update-1", "77", Instant.now());
    }
}
