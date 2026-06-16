package top.egon.mario.agent.memory;

import org.junit.jupiter.api.Test;
import top.egon.mario.agent.memory.po.AgentMemorySessionPo;
import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemorySessionStatus;
import top.egon.mario.agent.memory.repository.AgentMemorySessionRepository;
import top.egon.mario.agent.memory.service.impl.AgentMemorySessionServiceImpl;
import top.egon.mario.agent.memory.service.model.AgentMemorySessionCreate;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies memory session ownership and lifecycle rules.
 */
class AgentMemorySessionServiceTests {

    private final AgentMemorySessionRepository repository = mock(AgentMemorySessionRepository.class);
    private final AgentMemorySessionServiceImpl service = new AgentMemorySessionServiceImpl(repository);
    private final RbacPrincipal principal = new RbacPrincipal(8L, "luigi", Set.of("CHAT_BASIC"), Set.of(), "v1");

    @Test
    void createsActiveSessionForCurrentUserWithDefaults() {
        given(repository.save(any(AgentMemorySessionPo.class))).willAnswer(invocation -> invocation.getArgument(0));

        AgentMemorySessionPo saved = service.create(new AgentMemorySessionCreate(
                AgentMemoryEntryType.AGENT_CHAT, "Chat", null, null), principal);

        assertThat(saved.getSessionId()).isNotBlank();
        assertThat(saved.getUserId()).isEqualTo(8L);
        assertThat(saved.getUsername()).isEqualTo("luigi");
        assertThat(saved.getStatus()).isEqualTo(AgentMemorySessionStatus.ACTIVE);
        assertThat(saved.isMemoryEnabled()).isTrue();
        assertThat(saved.isLongTermExtractionEnabled()).isTrue();
        assertThat(saved.getShortTermWindowTurns()).isEqualTo(10);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void rejectsSessionOwnedByAnotherUser() {
        given(repository.findBySessionIdAndUserIdAndDeletedFalse("session-1", 8L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireOwned("session-1", principal))
                .hasMessageContaining("memory session not found");
    }

    @Test
    void archivedSessionCannotBeActivatedImplicitlyByRequireActive() {
        AgentMemorySessionPo session = session("session-1", AgentMemorySessionStatus.ARCHIVED);
        given(repository.findBySessionIdAndUserIdAndDeletedFalse("session-1", 8L)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> service.requireUsableForChat("session-1", principal))
                .hasMessageContaining("memory session is archived");
    }

    @Test
    void releasedSessionIsRestoredWhenUsedForChat() {
        AgentMemorySessionPo session = session("session-1", AgentMemorySessionStatus.RELEASED);
        given(repository.findBySessionIdAndUserIdAndDeletedFalse("session-1", 8L)).willReturn(Optional.of(session));
        given(repository.save(session)).willReturn(session);

        AgentMemorySessionPo restored = service.requireUsableForChat("session-1", principal);

        assertThat(restored.getStatus()).isEqualTo(AgentMemorySessionStatus.ACTIVE);
        assertThat(restored.getLastActiveAt()).isNotNull();
        verify(repository).save(session);
    }

    @Test
    void archivedSessionCanBeLogicallyDeleted() {
        AgentMemorySessionPo session = session("session-1", AgentMemorySessionStatus.ARCHIVED);
        given(repository.findBySessionIdAndUserIdAndDeletedFalse("session-1", 8L)).willReturn(Optional.of(session));

        service.deleteArchived("session-1", principal);

        assertThat(session.getStatus()).isEqualTo(AgentMemorySessionStatus.DELETED);
        assertThat(session.isDeleted()).isTrue();
        assertThat(session.getDeletedAt()).isNotNull();
        verify(repository).save(session);
    }

    private AgentMemorySessionPo session(String sessionId, AgentMemorySessionStatus status) {
        AgentMemorySessionPo session = new AgentMemorySessionPo();
        session.setSessionId(sessionId);
        session.setUserId(8L);
        session.setEntryType(AgentMemoryEntryType.AGENT_CHAT);
        session.setStatus(status);
        session.setMemoryEnabled(true);
        session.setLongTermExtractionEnabled(true);
        session.setShortTermWindowTurns(10);
        return session;
    }
}
