package top.egon.mario.agent.externalim.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.egon.mario.agent.externalim.ExternalChatException;
import top.egon.mario.agent.externalim.memory.impl.DefaultDirectionalAgentMemoryContextService;
import top.egon.mario.agent.externalim.memory.po.AgentMemorySpacePo;
import top.egon.mario.agent.memory.po.AgentLongTermMemoryPo;
import top.egon.mario.agent.memory.po.AgentMemoryMessagePo;
import top.egon.mario.agent.memory.po.AgentMemorySessionPo;
import top.egon.mario.agent.memory.po.enums.AgentLongTermMemoryScopeType;
import top.egon.mario.agent.memory.po.enums.AgentMemoryDomain;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageRole;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageStatus;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageType;
import top.egon.mario.agent.memory.repository.AgentMemoryMessageRepository;
import top.egon.mario.agent.memory.service.AgentLongTermMemoryService;
import top.egon.mario.agent.memory.service.AgentMemoryContextService;
import top.egon.mario.agent.memory.service.model.AgentMemoryContext;
import top.egon.mario.agent.externalim.model.ExternalChatPlatform;
import top.egon.mario.agent.externalim.model.ExternalConversationType;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DirectionalAgentMemoryContextServiceTests {

    @Mock
    private AgentMemoryContextService webContextService;
    @Mock
    private AgentMemoryMessageRepository messageRepository;
    @Mock
    private AgentLongTermMemoryService longTermMemoryService;
    @Mock
    private AgentMemorySpaceService spaceService;

    private DirectionalAgentMemoryContextService service;
    private AgentMemorySessionPo session;
    private RbacPrincipal principal;

    @BeforeEach
    void setUp() {
        service = new DefaultDirectionalAgentMemoryContextService(webContextService, messageRepository,
                longTermMemoryService, spaceService, new ExternalImMemoryProperties(40, 12000, 12, 3000));
        session = new AgentMemorySessionPo();
        session.setUserId(8L);
        session.setUsername("luigi");
        principal = new RbacPrincipal(8L, "luigi", Set.of(), Set.of(), "v1");
    }

    @Test
    void webWithoutSpaceDelegatesToTheUnchangedPrivateContext() {
        AgentMemoryContext web = new AgentMemoryContext("web recent", "web long");
        given(webContextService.contextFor(session, principal, true)).willReturn(web);

        AgentMemoryContext result = service.webContext(session, principal, null, true);

        assertThat(result).isEqualTo(web);
        verifyNoInteractions(messageRepository, longTermMemoryService, spaceService);
    }

    @Test
    void webWithOwnedSpaceReadsWebAndImButDoesNotWriteOrCopyMessages() {
        given(webContextService.contextFor(session, principal, true))
                .willReturn(new AgentMemoryContext("web recent", "web long"));
        given(spaceService.requireOwned("space-1", principal)).willReturn(space());
        given(messageRepository.findTop80ByMemorySpaceIdAndIdLessThanAndDeletedFalseOrderByIdDesc(
                "space-1", Long.MAX_VALUE)).willReturn(List.of(groupObservation()));
        given(longTermMemoryService.getOrCreate(8L, "luigi",
                AgentLongTermMemoryScopeType.IM_SHARED, "space-1")).willReturn(imMemory("shared preference"));

        AgentMemoryContext result = service.webContext(session, principal, "space-1", true);

        assertThat(result.shortTermPrompt()).contains("web recent", "[TELEGRAM][GROUP]", "deployment");
        assertThat(result.longTermPrompt()).contains("web long", "shared preference");
    }

    @Test
    void externalContextReadsOnlyTheImSpaceAndExcludesTheCurrentObservation() {
        session.setMemoryDomain(AgentMemoryDomain.IM_SHARED);
        session.setMemorySpaceId("space-1");
        given(messageRepository.findTop80ByMemorySpaceIdAndIdLessThanAndDeletedFalseOrderByIdDesc(
                "space-1", 101L)).willReturn(List.of(privateObservation()));
        given(longTermMemoryService.getOrCreate(8L, "luigi",
                AgentLongTermMemoryScopeType.IM_SHARED, "space-1")).willReturn(imMemory("shared memory"));

        AgentMemoryContext result = service.externalContext(session, 101L, true);

        assertThat(result.shortTermPrompt())
                .contains("[TELEGRAM][DIRECT]", "private detail", "不得主动向群聊受众披露");
        assertThat(result.longTermPrompt()).contains("shared memory");
        verifyNoInteractions(webContextService);
    }

    @Test
    void webCannotSelectAnotherOwnersSpace() {
        given(webContextService.contextFor(session, principal, true))
                .willReturn(new AgentMemoryContext("web recent", "web long"));
        given(spaceService.requireOwned("foreign-space", principal))
                .willThrow(new ExternalChatException("AGENT_MEMORY_SPACE_NOT_FOUND", "memory space not found"));

        assertThatThrownBy(() -> service.webContext(session, principal, "foreign-space", true))
                .isInstanceOf(ExternalChatException.class);
    }

    private AgentMemorySpacePo space() {
        AgentMemorySpacePo space = new AgentMemorySpacePo();
        space.setSpaceId("space-1");
        space.setOwnerUserId(8L);
        return space;
    }

    private AgentLongTermMemoryPo imMemory(String markdown) {
        AgentLongTermMemoryPo memory = new AgentLongTermMemoryPo();
        memory.setContentMarkdown(markdown);
        return memory;
    }

    private AgentMemoryMessagePo groupObservation() {
        return observation(ExternalConversationType.GROUP, "deployment");
    }

    private AgentMemoryMessagePo privateObservation() {
        return observation(ExternalConversationType.DIRECT, "private detail");
    }

    private AgentMemoryMessagePo observation(ExternalConversationType type, String content) {
        AgentMemoryMessagePo message = new AgentMemoryMessagePo();
        message.setMemoryDomain(AgentMemoryDomain.IM_SHARED);
        message.setMemorySpaceId("space-1");
        message.setSourcePlatform(ExternalChatPlatform.TELEGRAM);
        message.setSourceConversationType(type);
        message.setAudienceKey("telegram:main:-1001");
        message.setExternalSenderDisplayName("Alice");
        message.setRole(AgentMemoryMessageRole.USER);
        message.setMessageType(AgentMemoryMessageType.MESSAGE);
        message.setMessageStatus(AgentMemoryMessageStatus.SUCCEEDED);
        message.setContent(content);
        return message;
    }
}
