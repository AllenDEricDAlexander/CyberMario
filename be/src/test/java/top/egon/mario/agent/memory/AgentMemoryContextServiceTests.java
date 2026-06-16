package top.egon.mario.agent.memory;

import org.junit.jupiter.api.Test;
import top.egon.mario.agent.memory.po.AgentLongTermMemoryPo;
import top.egon.mario.agent.memory.po.AgentMemorySessionPo;
import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemorySessionStatus;
import top.egon.mario.agent.memory.service.AgentLongTermMemoryService;
import top.egon.mario.agent.memory.service.AgentMemoryMessageService;
import top.egon.mario.agent.memory.service.impl.AgentMemoryContextServiceImpl;
import top.egon.mario.agent.memory.service.model.AgentMemoryTurn;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Verifies memory prompt assembly boundaries by entry type.
 */
class AgentMemoryContextServiceTests {

    private final AgentMemoryMessageService messageService = mock(AgentMemoryMessageService.class);
    private final AgentLongTermMemoryService longTermMemoryService = mock(AgentLongTermMemoryService.class);
    private final AgentMemoryContextServiceImpl service =
            new AgentMemoryContextServiceImpl(messageService, longTermMemoryService);
    private final RbacPrincipal principal = new RbacPrincipal(8L, "luigi", Set.of("CHAT_BASIC"), Set.of(), "v1");

    @Test
    void doesNotAssembleHistoryWhenSessionMemoryDisabled() {
        AgentMemorySessionPo session = new AgentMemorySessionPo();
        session.setMemoryEnabled(false);

        var context = service.contextFor(session, principal);

        assertThat(context.shortTermPrompt()).isBlank();
        assertThat(context.longTermPrompt()).isBlank();
    }

    @Test
    void assemblesRecentTurnsAndLongTermMarkdownForAgentSession() {
        AgentMemorySessionPo session = session(AgentMemoryEntryType.AGENT_CHAT);
        given(messageService.recentTurns(session)).willReturn(List.of(
                new AgentMemoryTurn("我喜欢中文回答", "好的"),
                new AgentMemoryTurn("以后直接给结论", "明白")
        ));
        given(longTermMemoryService.getOrCreateUserAgentMemory(principal))
                .willReturn(memory("# User Memory\n\n## Preferences\n- 中文回答"));

        var context = service.contextFor(session, principal);

        assertThat(context.shortTermPrompt()).contains("用户: 我喜欢中文回答");
        assertThat(context.shortTermPrompt()).contains("助手: 明白");
        assertThat(context.longTermPrompt()).contains("以下是当前用户的长期记忆");
        assertThat(context.longTermPrompt()).contains("中文回答");
    }

    @Test
    void ragSessionOnlyAssemblesShortTermPrompt() {
        AgentMemorySessionPo session = session(AgentMemoryEntryType.RAG_CHAT);
        given(messageService.recentTurns(session)).willReturn(List.of(
                new AgentMemoryTurn("上一个问题", "上一个回答")
        ));

        var context = service.contextFor(session, principal);

        assertThat(context.shortTermPrompt()).contains("上一个问题");
        assertThat(context.longTermPrompt()).isBlank();
    }

    private AgentMemorySessionPo session(AgentMemoryEntryType entryType) {
        AgentMemorySessionPo session = new AgentMemorySessionPo();
        session.setSessionId("session-1");
        session.setEntryType(entryType);
        session.setStatus(AgentMemorySessionStatus.ACTIVE);
        session.setMemoryEnabled(true);
        session.setShortTermWindowTurns(10);
        return session;
    }

    private AgentLongTermMemoryPo memory(String markdown) {
        AgentLongTermMemoryPo memory = new AgentLongTermMemoryPo();
        memory.setContentMarkdown(markdown);
        memory.setContentChars(markdown.length());
        return memory;
    }
}
