package top.egon.mario.agent.externalim.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import top.egon.mario.agent.externalim.memory.ExternalChatBindingResolver;
import top.egon.mario.agent.externalim.memory.model.ResolvedExternalChatBinding;
import top.egon.mario.agent.externalim.model.ExternalChatMessage;
import top.egon.mario.agent.externalim.model.ExternalChatPlatform;
import top.egon.mario.agent.externalim.model.ExternalConversationType;
import top.egon.mario.agent.externalim.model.ExternalMessageType;
import top.egon.mario.agent.externalim.model.ExternalSender;
import top.egon.mario.agent.externalim.model.ExternalSenderType;
import top.egon.mario.agent.externalim.runtime.impl.DefaultExternalChatIngressService;
import top.egon.mario.agent.externalim.runtime.model.ExternalChatAcceptance;
import top.egon.mario.agent.externalim.runtime.po.ExternalChatEventPo;
import top.egon.mario.agent.externalim.runtime.po.enums.ExternalChatProcessingStatus;
import top.egon.mario.agent.externalim.runtime.po.enums.ExternalChatReplyStatus;
import top.egon.mario.agent.externalim.runtime.repository.ExternalChatEventRepository;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ExternalChatIngressServiceTests {

    private final ExternalChatEventRepository repository = mock(ExternalChatEventRepository.class);
    private final ExternalChatBindingResolver bindingResolver = mock(ExternalChatBindingResolver.class);
    private final DefaultExternalChatIngressService service = new DefaultExternalChatIngressService(
            repository, bindingResolver, new ObjectMapper().findAndRegisterModules());

    @Test
    void boundHumanTextIsDurablyAcceptedBeforeAck() {
        ExternalChatMessage message = message(ExternalSenderType.HUMAN, ExternalMessageType.TEXT, "hello");
        given(bindingResolver.resolve(message)).willReturn(binding());
        given(repository.findByPlatformAndConnectorIdAndExternalEventId(
                ExternalChatPlatform.TELEGRAM, "main", "update-1")).willReturn(Optional.empty());
        given(repository.saveAndFlush(any(ExternalChatEventPo.class))).willAnswer(invocation -> {
            ExternalChatEventPo value = invocation.getArgument(0);
            value.setId(10L);
            return value;
        });

        ExternalChatAcceptance accepted = service.accept(message, "trace-1");

        assertThat(accepted.eventDatabaseId()).isEqualTo(10L);
        assertThat(accepted.duplicate()).isFalse();
        assertThat(accepted.status()).isEqualTo(ExternalChatProcessingStatus.RECEIVED);
        verify(repository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(event ->
                event.getOwnerUserId().equals(8L)
                        && event.getSpaceId().equals("space-1")
                        && event.getNormalizedMessageJson().contains("\"text\":\"hello\"")
                        && !event.getNormalizedMessageJson().toLowerCase().contains("authorization")));
    }

    @Test
    void duplicateEventReturnsExistingRowWithoutAnotherSave() {
        ExternalChatEventPo existing = event(10L, ExternalChatProcessingStatus.SUCCEEDED);
        given(repository.findByPlatformAndConnectorIdAndExternalEventId(
                ExternalChatPlatform.TELEGRAM, "main", "update-1")).willReturn(Optional.of(existing));

        ExternalChatAcceptance accepted = service.accept(
                message(ExternalSenderType.HUMAN, ExternalMessageType.TEXT, "hello"), "trace-1");

        assertThat(accepted.duplicate()).isTrue();
        assertThat(accepted.eventDatabaseId()).isEqualTo(10L);
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(bindingResolver);
    }

    @Test
    void botAndUnsupportedEventsAreAuditedButNeverQueuedForMemory() {
        given(repository.findByPlatformAndConnectorIdAndExternalEventId(any(), anyString(), anyString()))
                .willReturn(Optional.empty());
        given(repository.saveAndFlush(any())).willAnswer(invocation -> invocation.getArgument(0));

        ExternalChatAcceptance accepted = service.accept(
                message(ExternalSenderType.BOT, ExternalMessageType.TEXT, "bot echo"), "trace-1");

        assertThat(accepted.status()).isEqualTo(ExternalChatProcessingStatus.IGNORED);
        verify(repository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(event ->
                event.getProcessingStatus() == ExternalChatProcessingStatus.IGNORED
                        && event.getReplyStatus() == ExternalChatReplyStatus.NOT_REQUIRED));
        verifyNoInteractions(bindingResolver);
    }

    private ResolvedExternalChatBinding binding() {
        return new ResolvedExternalChatBinding(8L, "space-1", ExternalChatPlatform.TELEGRAM,
                "main", "group-1", ExternalConversationType.GROUP, "telegram:main:group-1");
    }

    private ExternalChatMessage message(ExternalSenderType senderType, ExternalMessageType messageType,
                                        String text) {
        return new ExternalChatMessage("update-1", "message-1", ExternalChatPlatform.TELEGRAM,
                "main", "group-1", ExternalConversationType.GROUP, "telegram:main:group-1",
                new ExternalSender("sender-1", "Mario", senderType), messageType, text,
                false, false, Instant.parse("2026-07-20T00:00:00Z"));
    }

    private ExternalChatEventPo event(Long id, ExternalChatProcessingStatus status) {
        ExternalChatEventPo event = new ExternalChatEventPo();
        event.setId(id);
        event.setProcessingStatus(status);
        return event;
    }
}
