package top.egon.mario.agent.memory;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.egon.mario.agent.memory.dto.response.AgentMemoryMessageResponse;
import top.egon.mario.agent.memory.po.AgentMemoryMessagePo;
import top.egon.mario.agent.memory.po.AgentMemorySessionPo;
import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageRole;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageStatus;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageType;
import top.egon.mario.agent.memory.repository.AgentMemoryMessageRepository;
import top.egon.mario.agent.memory.repository.AgentMemorySessionRepository;
import top.egon.mario.agent.memory.service.impl.AgentMemoryMessageServiceImpl;
import top.egon.mario.agent.memory.service.model.AgentMemoryMessageRecord;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies memory message sequencing and short-term turn windows.
 */
class AgentMemoryMessageServiceTests {

    private final AgentMemoryMessageRepository messageRepository = mock(AgentMemoryMessageRepository.class);
    private final AgentMemorySessionRepository sessionRepository = mock(AgentMemorySessionRepository.class);
    private final AgentMemoryMessageServiceImpl service =
            new AgentMemoryMessageServiceImpl(messageRepository, sessionRepository);
    private final RbacPrincipal principal = new RbacPrincipal(8L, "luigi", Set.of("CHAT_BASIC"), Set.of(), "v1");

    @Test
    void appendsRecordsInInputOrderWithSequentialNumbers() {
        given(messageRepository.findBySessionIdAndDeletedFalseOrderBySeqNoAsc("session-1"))
                .willReturn(List.of(message(4, 2, AgentMemoryMessageRole.ASSISTANT, "old")));
        given(messageRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

        service.appendAll(List.of(
                new AgentMemoryMessageRecord("session-1", 8L, AgentMemoryEntryType.AGENT_CHAT, 3,
                        AgentMemoryMessageRole.USER, AgentMemoryMessageType.MESSAGE, "hello", null, "trace-1", "request-1"),
                new AgentMemoryMessageRecord("session-1", 8L, AgentMemoryEntryType.AGENT_CHAT, 3,
                        AgentMemoryMessageRole.ASSISTANT, AgentMemoryMessageType.MESSAGE, "answer", null, "trace-1", "request-1")
        ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AgentMemoryMessagePo>> captor = ArgumentCaptor.forClass(List.class);
        verify(messageRepository).saveAll(captor.capture());
        List<AgentMemoryMessagePo> messages = captor.getValue();
        assertThat(messages).extracting(AgentMemoryMessagePo::getSeqNo).containsExactly(5, 6);
        assertThat(messages).extracting(AgentMemoryMessagePo::getContent).containsExactly("hello", "answer");
        assertThat(messages).extracting(AgentMemoryMessagePo::getMessageStatus)
                .containsExactly(AgentMemoryMessageStatus.SUCCEEDED, AgentMemoryMessageStatus.SUCCEEDED);
        assertThat(messages).extracting(AgentMemoryMessagePo::getErrorCode).containsOnlyNulls();
        assertThat(messages).extracting(AgentMemoryMessagePo::getErrorMessage).containsOnlyNulls();
        assertThat(messages).extracting(AgentMemoryMessagePo::getMetadataJson).containsOnlyNulls();
    }

    @Test
    void appendsFailedRecordsWithErrorMetadata() {
        given(messageRepository.findBySessionIdAndDeletedFalseOrderBySeqNoAsc("session-1"))
                .willReturn(List.of());
        given(messageRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

        service.appendAll(List.of(AgentMemoryMessageRecord.failed("session-1", 8L, AgentMemoryEntryType.AGENT_CHAT, 3,
                AgentMemoryMessageRole.ASSISTANT, AgentMemoryMessageType.MESSAGE, "模型调用失败：boom",
                "trace-1", "request-1", IllegalStateException.class.getName(), "boom")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AgentMemoryMessagePo>> captor = ArgumentCaptor.forClass(List.class);
        verify(messageRepository).saveAll(captor.capture());
        AgentMemoryMessagePo message = captor.getValue().getFirst();
        assertThat(message.getMessageStatus()).isEqualTo(AgentMemoryMessageStatus.FAILED);
        assertThat(message.getErrorCode()).isEqualTo(IllegalStateException.class.getName());
        assertThat(message.getErrorMessage()).isEqualTo("boom");
        assertThat(message.getMetadataJson()).isNull();
    }

    @Test
    void responseIncludesFinalSnapshotMetadata() {
        AgentMemoryMessagePo message = message(1, 1, AgentMemoryMessageRole.ASSISTANT, "answer");
        message.setId(12L);
        message.setTraceId("trace-1");
        message.setRequestId("request-1");
        message.setMessageStatus(AgentMemoryMessageStatus.FAILED);
        message.setErrorCode("AGENT_ERROR");
        message.setErrorMessage("boom");
        message.setMetadataJson("{\"finalSnapshot\":true}");

        AgentMemoryMessageResponse response = AgentMemoryMessageResponse.from(message);

        assertThat(response.messageStatus()).isEqualTo(AgentMemoryMessageStatus.FAILED);
        assertThat(response.errorCode()).isEqualTo("AGENT_ERROR");
        assertThat(response.errorMessage()).isEqualTo("boom");
        assertThat(response.metadataJson()).isEqualTo("{\"finalSnapshot\":true}");
    }

    @Test
    void messagesRequiresOwnedSession() {
        given(sessionRepository.findBySessionIdAndUserIdAndDeletedFalse("session-1", 8L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.messages("session-1", principal))
                .hasMessageContaining("memory session not found");
    }

    @Test
    void recentTurnsReturnsLastConfiguredCompletePairs() {
        AgentMemorySessionPo session = new AgentMemorySessionPo();
        session.setSessionId("session-1");
        session.setShortTermWindowTurns(2);
        given(messageRepository.findTop40BySessionIdAndDeletedFalseOrderBySeqNoDesc("session-1"))
                .willReturn(List.of(
                        message(6, 3, AgentMemoryMessageRole.ASSISTANT, "a3"),
                        message(5, 3, AgentMemoryMessageRole.USER, "u3"),
                        message(4, 2, AgentMemoryMessageRole.ASSISTANT, "a2"),
                        message(3, 2, AgentMemoryMessageRole.USER, "u2"),
                        message(2, 1, AgentMemoryMessageRole.ASSISTANT, "a1"),
                        message(1, 1, AgentMemoryMessageRole.USER, "u1")
                ));

        var turns = service.recentTurns(session);

        assertThat(turns).hasSize(2);
        assertThat(turns.get(0).userMessage()).isEqualTo("u2");
        assertThat(turns.get(0).assistantMessage()).isEqualTo("a2");
        assertThat(turns.get(1).userMessage()).isEqualTo("u3");
        assertThat(turns.get(1).assistantMessage()).isEqualTo("a3");
    }

    @Test
    void nextTurnNoStartsAfterCurrentMaximumTurn() {
        given(messageRepository.findBySessionIdAndDeletedFalseOrderBySeqNoAsc("session-1"))
                .willReturn(List.of(
                        message(1, 1, AgentMemoryMessageRole.USER, "u1"),
                        message(2, 4, AgentMemoryMessageRole.USER, "u4")
                ));

        assertThat(service.nextTurnNo("session-1")).isEqualTo(5);
    }

    private AgentMemoryMessagePo message(int seqNo, int turnNo, AgentMemoryMessageRole role, String content) {
        AgentMemoryMessagePo message = new AgentMemoryMessagePo();
        message.setSessionId("session-1");
        message.setUserId(8L);
        message.setEntryType(AgentMemoryEntryType.AGENT_CHAT);
        message.setSeqNo(seqNo);
        message.setTurnNo(turnNo);
        message.setRole(role);
        message.setMessageType(AgentMemoryMessageType.MESSAGE);
        message.setContent(content);
        message.setContentChars(content.length());
        message.setCreatedAt(Instant.parse("2026-06-16T01:00:00Z"));
        return message;
    }
}
