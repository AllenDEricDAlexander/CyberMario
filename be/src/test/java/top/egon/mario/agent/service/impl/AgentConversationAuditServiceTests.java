package top.egon.mario.agent.service.impl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.egon.mario.agent.po.AgentConversationAuditPo;
import top.egon.mario.agent.po.AgentConversationMessageAuditPo;
import top.egon.mario.agent.po.enums.AgentConversationMessageType;
import top.egon.mario.agent.po.enums.AgentConversationRole;
import top.egon.mario.agent.po.enums.AgentConversationStatus;
import top.egon.mario.agent.repository.AgentConversationAuditRepository;
import top.egon.mario.agent.repository.AgentConversationMessageAuditRepository;
import top.egon.mario.agent.service.model.AgentConversationAuditStart;
import top.egon.mario.agent.service.model.AgentConversationMessageRecord;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies agent conversation audit persistence mapping.
 */
class AgentConversationAuditServiceTests {

    @Test
    void startCreatesAuditAndInitialUserMessage() {
        AgentConversationAuditRepository auditRepository = mock(AgentConversationAuditRepository.class);
        AgentConversationMessageAuditRepository messageRepository = mock(AgentConversationMessageAuditRepository.class);
        given(auditRepository.save(any(AgentConversationAuditPo.class))).willAnswer(invocation -> {
            AgentConversationAuditPo po = invocation.getArgument(0);
            po.setId(12L);
            return po;
        });
        AgentConversationAuditServiceImpl service = new AgentConversationAuditServiceImpl(auditRepository, messageRepository);
        AgentConversationAuditStart start = new AgentConversationAuditStart(
                "request-1",
                "trace-1",
                8L,
                "luigi",
                "thread-1",
                3L,
                "fingerprint-1",
                "{\"temperature\":0.7}",
                "127.0.0.1",
                "JUnit",
                Instant.parse("2026-06-14T01:00:00Z")
        );

        Long auditId = service.start(start, "hello");

        assertThat(auditId).isEqualTo(12L);
        ArgumentCaptor<AgentConversationAuditPo> auditCaptor = ArgumentCaptor.forClass(AgentConversationAuditPo.class);
        verify(auditRepository).save(auditCaptor.capture());
        AgentConversationAuditPo audit = auditCaptor.getValue();
        assertThat(audit.getRequestId()).isEqualTo("request-1");
        assertThat(audit.getTraceId()).isEqualTo("trace-1");
        assertThat(audit.getUserId()).isEqualTo(8L);
        assertThat(audit.getUsername()).isEqualTo("luigi");
        assertThat(audit.getThreadId()).isEqualTo("thread-1");
        assertThat(audit.getPresetId()).isEqualTo(3L);
        assertThat(audit.getRuntimeFingerprint()).isEqualTo("fingerprint-1");
        assertThat(audit.getEffectiveConfigJson()).isEqualTo("{\"temperature\":0.7}");
        assertThat(audit.getStatus()).isEqualTo(AgentConversationStatus.RUNNING);
        assertThat(audit.getStartedAt()).isEqualTo(Instant.parse("2026-06-14T01:00:00Z"));
        assertThat(audit.getCreatedAt()).isNotNull();

        ArgumentCaptor<AgentConversationMessageAuditPo> messageCaptor = ArgumentCaptor.forClass(AgentConversationMessageAuditPo.class);
        verify(messageRepository).save(messageCaptor.capture());
        AgentConversationMessageAuditPo message = messageCaptor.getValue();
        assertThat(message.getConversationAuditId()).isEqualTo(12L);
        assertThat(message.getSeqNo()).isZero();
        assertThat(message.getRole()).isEqualTo(AgentConversationRole.USER);
        assertThat(message.getMessageType()).isEqualTo(AgentConversationMessageType.MESSAGE);
        assertThat(message.getContent()).isEqualTo("hello");
        assertThat(message.getContentChars()).isEqualTo(5);
        assertThat(message.getCreatedAt()).isNotNull();
    }

    @Test
    void completePersistsAssistantAndThinkMessages() {
        AgentConversationAuditRepository auditRepository = mock(AgentConversationAuditRepository.class);
        AgentConversationMessageAuditRepository messageRepository = mock(AgentConversationMessageAuditRepository.class);
        AgentConversationAuditPo audit = new AgentConversationAuditPo();
        audit.setId(12L);
        audit.setStartedAt(Instant.parse("2026-06-14T01:00:00Z"));
        audit.setStatus(AgentConversationStatus.RUNNING);
        given(auditRepository.findById(12L)).willReturn(java.util.Optional.of(audit));
        AgentConversationAuditServiceImpl service = new AgentConversationAuditServiceImpl(auditRepository, messageRepository);

        service.complete(12L, List.of(
                new AgentConversationMessageRecord(AgentConversationRole.ASSISTANT, AgentConversationMessageType.THINK, "thinking"),
                new AgentConversationMessageRecord(AgentConversationRole.ASSISTANT, AgentConversationMessageType.MESSAGE, "answer")
        ), Instant.parse("2026-06-14T01:00:03Z"));

        assertThat(audit.getStatus()).isEqualTo(AgentConversationStatus.SUCCESS);
        assertThat(audit.getFinishedAt()).isEqualTo(Instant.parse("2026-06-14T01:00:03Z"));
        assertThat(audit.getDurationMs()).isEqualTo(3000L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AgentConversationMessageAuditPo>> messageCaptor = ArgumentCaptor.forClass(List.class);
        verify(messageRepository).saveAll(messageCaptor.capture());
        assertThat(messageCaptor.getValue()).hasSize(2);
        assertThat(messageCaptor.getValue().get(0).getSeqNo()).isEqualTo(1);
        assertThat(messageCaptor.getValue().get(0).getMessageType()).isEqualTo(AgentConversationMessageType.THINK);
        assertThat(messageCaptor.getValue().get(1).getSeqNo()).isEqualTo(2);
        assertThat(messageCaptor.getValue().get(1).getContent()).isEqualTo("answer");
        verify(auditRepository).save(audit);
    }

    @Test
    void failTruncatesErrorMessage() {
        AgentConversationAuditRepository auditRepository = mock(AgentConversationAuditRepository.class);
        AgentConversationMessageAuditRepository messageRepository = mock(AgentConversationMessageAuditRepository.class);
        AgentConversationAuditPo audit = new AgentConversationAuditPo();
        audit.setId(12L);
        audit.setStartedAt(Instant.parse("2026-06-14T01:00:00Z"));
        audit.setStatus(AgentConversationStatus.RUNNING);
        given(auditRepository.findById(12L)).willReturn(java.util.Optional.of(audit));
        AgentConversationAuditServiceImpl service = new AgentConversationAuditServiceImpl(auditRepository, messageRepository);

        service.fail(12L, IllegalStateException.class.getName(), "x".repeat(1200),
                Instant.parse("2026-06-14T01:00:01Z"));

        assertThat(audit.getStatus()).isEqualTo(AgentConversationStatus.FAILED);
        assertThat(audit.getErrorCode()).isEqualTo(IllegalStateException.class.getName());
        assertThat(audit.getErrorMessage()).hasSize(1024);
        assertThat(audit.getDurationMs()).isEqualTo(1000L);
        verify(auditRepository).save(audit);
    }

    @Test
    void cancelMarksConversationCancelled() {
        AgentConversationAuditRepository auditRepository = mock(AgentConversationAuditRepository.class);
        AgentConversationMessageAuditRepository messageRepository = mock(AgentConversationMessageAuditRepository.class);
        AgentConversationAuditPo audit = new AgentConversationAuditPo();
        audit.setId(12L);
        audit.setStartedAt(Instant.parse("2026-06-14T01:00:00Z"));
        audit.setStatus(AgentConversationStatus.RUNNING);
        given(auditRepository.findById(12L)).willReturn(java.util.Optional.of(audit));
        AgentConversationAuditServiceImpl service = new AgentConversationAuditServiceImpl(auditRepository, messageRepository);

        service.cancel(12L, Instant.parse("2026-06-14T01:00:02Z"));

        assertThat(audit.getStatus()).isEqualTo(AgentConversationStatus.CANCELLED);
        assertThat(audit.getFinishedAt()).isEqualTo(Instant.parse("2026-06-14T01:00:02Z"));
        assertThat(audit.getDurationMs()).isEqualTo(2000L);
        verify(auditRepository).save(audit);
    }

}
