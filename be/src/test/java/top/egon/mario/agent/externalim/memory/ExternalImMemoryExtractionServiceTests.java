package top.egon.mario.agent.externalim.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.egon.mario.agent.externalim.memory.impl.DefaultExternalImMemoryExtractionService;
import top.egon.mario.agent.externalim.memory.model.ExternalImMemoryExtractionRequest;
import top.egon.mario.agent.memory.po.AgentLongTermMemoryPo;
import top.egon.mario.agent.memory.po.AgentMemoryMessagePo;
import top.egon.mario.agent.memory.po.AgentMemorySessionPo;
import top.egon.mario.agent.memory.po.enums.AgentLongTermMemoryScopeType;
import top.egon.mario.agent.memory.po.enums.AgentMemoryDomain;
import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageRole;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageStatus;
import top.egon.mario.agent.memory.repository.AgentMemoryExtractionAuditRepository;
import top.egon.mario.agent.memory.service.AgentLongTermMemoryService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ExternalImMemoryExtractionServiceTests {

    @Mock
    private AgentLongTermMemoryService longTermMemoryService;
    @Mock
    private AgentMemoryExtractionAuditRepository auditRepository;

    private ExternalImMemoryExtractionService service;

    @BeforeEach
    void setUp() {
        service = new DefaultExternalImMemoryExtractionService(longTermMemoryService, auditRepository);
    }

    @Test
    void extractsOnlyTheSuccessfulCurrentImReplyTurn() {
        AgentMemorySessionPo session = externalSession();
        AgentMemoryMessagePo user = externalMessage(101L, AgentMemoryMessageRole.USER,
                "请记住我偏好简短回答", AgentMemoryMessageStatus.SUCCEEDED);
        AgentMemoryMessagePo assistant = externalMessage(102L, AgentMemoryMessageRole.ASSISTANT,
                "好的", AgentMemoryMessageStatus.SUCCEEDED);
        given(longTermMemoryService.getOrCreate(8L, null,
                AgentLongTermMemoryScopeType.IM_SHARED, "space-1")).willReturn(imMemory("# Memory"));
        given(longTermMemoryService.merge(any())).willReturn(imMemory("# Memory\n- 用户表达: 请记住我偏好简短回答"));

        service.extractAfterReply(new ExternalImMemoryExtractionRequest(
                session, user, assistant, "request-1", "trace-1"));

        verify(longTermMemoryService).merge(argThat(request ->
                request.scopeType() == AgentLongTermMemoryScopeType.IM_SHARED
                        && request.memorySpaceId().equals("space-1")
                        && request.sourceMessageIds().equals("101,102")));
    }

    @Test
    void doesNotExtractIgnoredObservationOrWebMemory() {
        AgentMemorySessionPo web = externalSession();
        web.setMemoryDomain(AgentMemoryDomain.WEB_PRIVATE);

        service.extractAfterReply(new ExternalImMemoryExtractionRequest(
                web, externalMessage(101L, AgentMemoryMessageRole.USER, "请记住",
                AgentMemoryMessageStatus.SUCCEEDED), null, "request-1", "trace-1"));

        verifyNoInteractions(longTermMemoryService);
    }

    private AgentMemorySessionPo externalSession() {
        AgentMemorySessionPo session = new AgentMemorySessionPo();
        session.setSessionId("__external_im__:space-1");
        session.setUserId(8L);
        session.setEntryType(AgentMemoryEntryType.AGENT_CHAT);
        session.setMemoryDomain(AgentMemoryDomain.IM_SHARED);
        session.setMemorySpaceId("space-1");
        return session;
    }

    private AgentMemoryMessagePo externalMessage(Long id, AgentMemoryMessageRole role, String content,
                                                  AgentMemoryMessageStatus status) {
        AgentMemoryMessagePo message = new AgentMemoryMessagePo();
        message.setId(id);
        message.setRole(role);
        message.setContent(content);
        message.setMessageStatus(status);
        message.setExternalEventId("update-1");
        return message;
    }

    private AgentLongTermMemoryPo imMemory(String markdown) {
        AgentLongTermMemoryPo memory = new AgentLongTermMemoryPo();
        memory.setContentMarkdown(markdown);
        memory.setActiveVersionId(11L);
        return memory;
    }
}
