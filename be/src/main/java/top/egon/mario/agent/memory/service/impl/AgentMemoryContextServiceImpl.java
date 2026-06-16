package top.egon.mario.agent.memory.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.memory.po.AgentLongTermMemoryPo;
import top.egon.mario.agent.memory.po.AgentMemorySessionPo;
import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemorySessionStatus;
import top.egon.mario.agent.memory.service.AgentLongTermMemoryService;
import top.egon.mario.agent.memory.service.AgentMemoryContextService;
import top.egon.mario.agent.memory.service.AgentMemoryMessageService;
import top.egon.mario.agent.memory.service.model.AgentMemoryContext;
import top.egon.mario.agent.memory.service.model.AgentMemoryTurn;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds prompt fragments from persisted short-term and long-term user memory.
 */
@Service
public class AgentMemoryContextServiceImpl implements AgentMemoryContextService {

    private static final AgentMemoryContext EMPTY = new AgentMemoryContext("", "");

    private final AgentMemoryMessageService messageService;
    private final AgentLongTermMemoryService longTermMemoryService;

    public AgentMemoryContextServiceImpl(AgentMemoryMessageService messageService,
                                         AgentLongTermMemoryService longTermMemoryService) {
        this.messageService = messageService;
        this.longTermMemoryService = longTermMemoryService;
    }

    @Override
    @Transactional(readOnly = true)
    public AgentMemoryContext contextFor(AgentMemorySessionPo session, RbacPrincipal principal) {
        if (session == null || !session.isMemoryEnabled() || session.isDeleted()
                || session.getStatus() == AgentMemorySessionStatus.ARCHIVED
                || session.getStatus() == AgentMemorySessionStatus.DELETED) {
            return EMPTY;
        }
        String shortTermPrompt = shortTermPrompt(messageService.recentTurns(session));
        if (session.getEntryType() == AgentMemoryEntryType.RAG_CHAT) {
            return new AgentMemoryContext(shortTermPrompt, "");
        }
        if (session.getEntryType() != AgentMemoryEntryType.AGENT_CHAT
                && session.getEntryType() != AgentMemoryEntryType.AGENT_DEBUG
                && session.getEntryType() != AgentMemoryEntryType.BUTLER_AGENT) {
            return new AgentMemoryContext(shortTermPrompt, "");
        }
        AgentLongTermMemoryPo memory = longTermMemoryService.getOrCreateUserAgentMemory(principal);
        String longTermPrompt = longTermPrompt(memory == null ? null : memory.getContentMarkdown());
        return new AgentMemoryContext(shortTermPrompt, longTermPrompt);
    }

    private String shortTermPrompt(List<AgentMemoryTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            return "";
        }
        String body = turns.stream()
                .map(turn -> "用户: %s\n助手: %s".formatted(turn.userMessage(), turn.assistantMessage()))
                .collect(Collectors.joining("\n"));
        return """
                以下是当前会话的最近对话，仅用于保持本会话连续性。
                %s
                """.formatted(body).trim();
    }

    private String longTermPrompt(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return "";
        }
        return """
                以下是当前用户的长期记忆，仅用于理解用户偏好和稳定背景。
                不得把这些记忆当成外部事实来源；涉及知识库事实时必须以 RAG sources 为准。
                %s
                """.formatted(markdown.trim()).trim();
    }
}
