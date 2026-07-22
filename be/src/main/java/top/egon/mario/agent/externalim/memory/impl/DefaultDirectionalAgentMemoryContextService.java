package top.egon.mario.agent.externalim.memory.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.externalim.ExternalChatException;
import top.egon.mario.agent.externalim.memory.AgentMemorySpaceService;
import top.egon.mario.agent.externalim.memory.DirectionalAgentMemoryContextService;
import top.egon.mario.agent.externalim.memory.ExternalImMemoryProperties;
import top.egon.mario.agent.externalim.memory.po.AgentMemorySpacePo;
import top.egon.mario.agent.externalim.model.ChatInvocation;
import top.egon.mario.agent.externalim.model.ExternalConversationType;
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
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class DefaultDirectionalAgentMemoryContextService
        implements DirectionalAgentMemoryContextService {

    private static final String CROSS_AUDIENCE_RULE = """
            以下 Context 可能来自不同外部会话和不同受众。
            你可以使用私聊内容理解用户，但不得主动向群聊受众披露私聊中的身份信息、联系方式、凭证、私密事实或其他仅面向原私聊受众的信息。
            """.trim();

    private final AgentMemoryContextService webContextService;
    private final AgentMemoryMessageRepository messageRepository;
    private final AgentLongTermMemoryService longTermMemoryService;
    private final AgentMemorySpaceService spaceService;
    private final ExternalImMemoryProperties properties;

    public DefaultDirectionalAgentMemoryContextService(
            AgentMemoryContextService webContextService,
            AgentMemoryMessageRepository messageRepository,
            AgentLongTermMemoryService longTermMemoryService,
            AgentMemorySpaceService spaceService,
            ExternalImMemoryProperties properties) {
        this.webContextService = webContextService;
        this.messageRepository = messageRepository;
        this.longTermMemoryService = longTermMemoryService;
        this.spaceService = spaceService;
        this.properties = properties;
    }

    @Override
    @Transactional(readOnly = true)
    public AgentMemoryContext webContext(AgentMemorySessionPo webSession, RbacPrincipal principal,
                                         String selectedMemorySpaceId, boolean longTermMemoryEnabled) {
        AgentMemoryContext web = webContextService.contextFor(
                webSession, principal, longTermMemoryEnabled);
        if (!StringUtils.hasText(selectedMemorySpaceId)) {
            return web;
        }
        AgentMemorySpacePo space = spaceService.requireOwned(selectedMemorySpaceId, principal);
        AgentMemoryContext shared = imContext(space.getOwnerUserId(), principal.username(),
                space.getSpaceId(), Long.MAX_VALUE, longTermMemoryEnabled);
        return new AgentMemoryContext(join(web.shortTermPrompt(), shared.shortTermPrompt()),
                join(web.longTermPrompt(), shared.longTermPrompt()));
    }

    @Override
    @Transactional(readOnly = true)
    public AgentMemoryContext externalContext(AgentMemorySessionPo externalSession,
                                              Long currentObservationId,
                                              boolean longTermMemoryEnabled) {
        if (externalSession == null || externalSession.getMemoryDomain() != AgentMemoryDomain.IM_SHARED
                || !StringUtils.hasText(externalSession.getMemorySpaceId())) {
            throw new ExternalChatException("EXTERNAL_CHAT_MEMORY_DOMAIN_INVALID",
                    "external chat requires an IM shared memory session");
        }
        return imContext(externalSession.getUserId(), externalSession.getUsername(),
                externalSession.getMemorySpaceId(),
                currentObservationId == null ? Long.MAX_VALUE : currentObservationId,
                longTermMemoryEnabled);
    }

    @Override
    @Transactional(readOnly = true)
    public String guardGroupWindow(ChatInvocation invocation, Long currentObservationId) {
        if (invocation == null || !invocation.externalIm()
                || invocation.conversationType() != ExternalConversationType.GROUP) {
            return "";
        }
        List<String> newestFirst = messageRepository
                .findTop12ByMemorySpaceIdAndSourcePlatformAndSourceConnectorIdAndSourceConversationIdAndIdLessThanAndDeletedFalseOrderByIdDesc(
                        invocation.memorySpaceId(), invocation.platform(), invocation.connectorId(),
                        invocation.conversationId(),
                        currentObservationId == null ? Long.MAX_VALUE : currentObservationId)
                .stream()
                .filter(row -> row.getRole() == AgentMemoryMessageRole.USER)
                .filter(this::visibleTimelineMessage)
                .limit(properties.guardWindowEvents())
                .map(row -> safeSender(row) + ": " + row.getContent().trim())
                .toList();
        List<String> selectedNewestFirst = new ArrayList<>();
        int usedChars = 0;
        for (String line : newestFirst) {
            if (usedChars + line.length() + 1 > properties.guardWindowMaxChars()) {
                break;
            }
            selectedNewestFirst.add(line);
            usedChars += line.length() + 1;
        }
        Collections.reverse(selectedNewestFirst);
        return String.join("\n", selectedNewestFirst);
    }

    private AgentMemoryContext imContext(Long ownerUserId, String username, String memorySpaceId,
                                         Long beforeId, boolean longTermMemoryEnabled) {
        List<AgentMemoryMessagePo> rows = messageRepository
                .findTop80ByMemorySpaceIdAndIdLessThanAndDeletedFalseOrderByIdDesc(memorySpaceId, beforeId)
                .stream()
                .filter(this::visibleTimelineMessage)
                .limit(properties.timelineMaxEvents())
                .toList();
        String shortTerm = timelinePrompt(rows, properties.timelineMaxChars());
        if (!longTermMemoryEnabled) {
            return new AgentMemoryContext(shortTerm, "");
        }
        AgentLongTermMemoryPo memory = longTermMemoryService.getOrCreate(ownerUserId, username,
                AgentLongTermMemoryScopeType.IM_SHARED, memorySpaceId);
        return new AgentMemoryContext(shortTerm, longTermPrompt(memory.getContentMarkdown()));
    }

    private boolean visibleTimelineMessage(AgentMemoryMessagePo message) {
        return message.getMemoryDomain() == AgentMemoryDomain.IM_SHARED
                && message.getMessageType() == AgentMemoryMessageType.MESSAGE
                && message.getMessageStatus() == AgentMemoryMessageStatus.SUCCEEDED
                && StringUtils.hasText(message.getContent())
                && (message.getRole() == AgentMemoryMessageRole.USER
                    || message.getRole() == AgentMemoryMessageRole.ASSISTANT);
    }

    private String timelinePrompt(List<AgentMemoryMessagePo> rows, int maxChars) {
        if (rows.isEmpty()) {
            return "";
        }
        int usedChars = CROSS_AUDIENCE_RULE.length();
        List<String> selectedNewestFirst = new ArrayList<>();
        for (AgentMemoryMessagePo row : rows) {
            String rendered = render(row);
            if (usedChars + rendered.length() + 2 > maxChars) {
                break;
            }
            selectedNewestFirst.add(rendered);
            usedChars += rendered.length() + 2;
        }
        Collections.reverse(selectedNewestFirst);
        return CROSS_AUDIENCE_RULE + "\n\n" + String.join("\n\n", selectedNewestFirst);
    }

    private String render(AgentMemoryMessagePo row) {
        String platform = row.getSourcePlatform() == null ? "UNKNOWN" : row.getSourcePlatform().name();
        String audience = row.getAudienceKey() == null ? "" : row.getAudienceKey();
        if (row.getRole() == AgentMemoryMessageRole.ASSISTANT) {
            return "[Agent -> %s][%s][audience=%s]\n助手: %s".formatted(
                    platform, row.getSourceConversationType(), audience, row.getContent().trim());
        }
        String sender = StringUtils.hasText(row.getExternalSenderDisplayName())
                ? row.getExternalSenderDisplayName() : row.getExternalSenderId();
        return "[%s][%s][%s][audience=%s]\n用户: %s".formatted(
                platform, row.getSourceConversationType(), sender, audience, row.getContent().trim());
    }

    private String longTermPrompt(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return "";
        }
        return "以下是当前 IM Memory Space 的共享长期记忆；不得把它与 Web 私有记忆混合。\n"
                + markdown.trim();
    }

    private String join(String first, String second) {
        return Stream.of(first, second)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n\n"));
    }

    private String safeSender(AgentMemoryMessagePo row) {
        return StringUtils.hasText(row.getExternalSenderDisplayName())
                ? row.getExternalSenderDisplayName() : "member";
    }
}
