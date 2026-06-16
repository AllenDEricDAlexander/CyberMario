package top.egon.mario.agent.memory;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.egon.mario.agent.memory.po.AgentMemoryExtractionAuditPo;
import top.egon.mario.agent.memory.po.AgentLongTermMemoryPo;
import top.egon.mario.agent.memory.po.AgentMemoryMessagePo;
import top.egon.mario.agent.memory.po.AgentMemorySessionPo;
import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemoryExtractionStatus;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageRole;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageType;
import top.egon.mario.agent.memory.po.enums.AgentMemorySessionStatus;
import top.egon.mario.agent.memory.service.AgentMemoryDefaults;
import top.egon.mario.agent.memory.repository.AgentMemoryExtractionAuditRepository;
import top.egon.mario.agent.memory.repository.AgentMemoryMessageRepository;
import top.egon.mario.agent.memory.repository.AgentMemorySessionRepository;
import top.egon.mario.agent.memory.service.AgentLongTermMemoryService;
import top.egon.mario.agent.memory.service.impl.AgentMemoryExtractionServiceImpl;
import top.egon.mario.agent.memory.service.model.AgentLongTermMemoryMergeRequest;
import top.egon.mario.agent.memory.service.model.AgentMemoryExtractionRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Verifies deterministic first-pass extraction and audit outcomes.
 */
class AgentMemoryExtractionServiceTests {

    private final AgentMemorySessionRepository sessionRepository = mock(AgentMemorySessionRepository.class);
    private final AgentMemoryMessageRepository messageRepository = mock(AgentMemoryMessageRepository.class);
    private final AgentMemoryExtractionAuditRepository auditRepository = mock(AgentMemoryExtractionAuditRepository.class);
    private final AgentLongTermMemoryService longTermMemoryService = mock(AgentLongTermMemoryService.class);
    private final AgentMemoryExtractionServiceImpl service = new AgentMemoryExtractionServiceImpl(
            sessionRepository, messageRepository, auditRepository, longTermMemoryService);

    @Test
    void extractsRagMarkerIntoRagDerivedNotes() {
        AgentMemorySessionPo session = session(AgentMemoryEntryType.RAG_CHAT, true);
        given(sessionRepository.findBySessionIdAndDeletedFalse("rag-session-1")).willReturn(Optional.of(session));
        AgentMemoryMessagePo userMessage = message(1L, 1, AgentMemoryMessageRole.USER,
                "记住这个项目默认使用中文回答");
        given(messageRepository.findBySessionIdAndDeletedFalseOrderBySeqNoAsc("rag-session-1"))
                .willReturn(List.of(userMessage, message(2L, 1, AgentMemoryMessageRole.ASSISTANT, "好的")));
        given(auditRepository.save(any(AgentMemoryExtractionAuditPo.class))).willAnswer(invocation -> invocation.getArgument(0));
        AgentLongTermMemoryPo currentMemory = new AgentLongTermMemoryPo();
        currentMemory.setContentMarkdown(AgentMemoryDefaults.DEFAULT_USER_MEMORY_MARKDOWN);
        given(longTermMemoryService.getOrCreate(8L, "luigi", top.egon.mario.agent.memory.po.enums.AgentLongTermMemoryScopeType.USER_AGENT))
                .willReturn(currentMemory);
        AgentLongTermMemoryPo mergedMemory = new AgentLongTermMemoryPo();
        mergedMemory.setActiveVersionId(300L);
        given(longTermMemoryService.merge(any(AgentLongTermMemoryMergeRequest.class))).willReturn(mergedMemory);

        service.extractAfterTurn(new AgentMemoryExtractionRequest("rag-session-1", "request-1", "trace-1"));

        ArgumentCaptor<AgentLongTermMemoryMergeRequest> mergeCaptor =
                ArgumentCaptor.forClass(AgentLongTermMemoryMergeRequest.class);
        verify(longTermMemoryService).merge(mergeCaptor.capture());
        assertThat(mergeCaptor.getValue().mergedMarkdown()).contains("## RAG-Derived Notes");
        assertThat(mergeCaptor.getValue().mergedMarkdown())
                .contains("- 用户表达: 记住这个项目默认使用中文回答 [source: RAG_CHAT session=rag-session-1]");
        ArgumentCaptor<AgentMemoryExtractionAuditPo> auditCaptor =
                ArgumentCaptor.forClass(AgentMemoryExtractionAuditPo.class);
        verify(auditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getStatus()).isEqualTo(AgentMemoryExtractionStatus.SUCCESS);
    }

    @Test
    void skipsWhenExtractionDisabled() {
        AgentMemorySessionPo session = session(AgentMemoryEntryType.AGENT_CHAT, false);
        given(sessionRepository.findBySessionIdAndDeletedFalse("session-1")).willReturn(Optional.of(session));
        given(auditRepository.save(any(AgentMemoryExtractionAuditPo.class))).willAnswer(invocation -> invocation.getArgument(0));

        service.extractAfterTurn(new AgentMemoryExtractionRequest("session-1", "request-1", "trace-1"));

        verify(longTermMemoryService, never()).merge(any());
        ArgumentCaptor<AgentMemoryExtractionAuditPo> auditCaptor =
                ArgumentCaptor.forClass(AgentMemoryExtractionAuditPo.class);
        verify(auditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getStatus()).isEqualTo(AgentMemoryExtractionStatus.SKIPPED);
        assertThat(auditCaptor.getValue().getErrorCode()).isEqualTo("AGENT_MEMORY_EXTRACTION_DISABLED");
    }

    private AgentMemorySessionPo session(AgentMemoryEntryType entryType, boolean extractionEnabled) {
        AgentMemorySessionPo session = new AgentMemorySessionPo();
        session.setSessionId(entryType == AgentMemoryEntryType.RAG_CHAT ? "rag-session-1" : "session-1");
        session.setEntryType(entryType);
        session.setUserId(8L);
        session.setUsername("luigi");
        session.setStatus(AgentMemorySessionStatus.ACTIVE);
        session.setLongTermExtractionEnabled(extractionEnabled);
        return session;
    }

    private AgentMemoryMessagePo message(Long id, int turnNo, AgentMemoryMessageRole role, String content) {
        AgentMemoryMessagePo message = new AgentMemoryMessagePo();
        message.setId(id);
        message.setSessionId("session-1");
        message.setUserId(8L);
        message.setEntryType(AgentMemoryEntryType.AGENT_CHAT);
        message.setSeqNo(id.intValue());
        message.setTurnNo(turnNo);
        message.setRole(role);
        message.setMessageType(AgentMemoryMessageType.MESSAGE);
        message.setContent(content);
        message.setContentChars(content.length());
        message.setCreatedAt(Instant.parse("2026-06-16T01:00:00Z"));
        return message;
    }
}
