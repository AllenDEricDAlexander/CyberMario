package top.egon.mario.agent.externalim.flow;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.externalim.ExternalChatException;
import top.egon.mario.agent.externalim.model.ChatInvocation;
import top.egon.mario.agent.externalim.model.ChatSource;
import top.egon.mario.agent.service.AgentException;
import top.egon.mario.pojo.request.ChatRequest;
import top.egon.mario.rbac.service.security.RbacPrincipal;

@Service
public class DefaultChatInvocationPolicy implements ChatInvocationPolicy {

    @Override
    public ChatInvocation fromWeb(ChatRequest request, RbacPrincipal principal) {
        if (request == null || !StringUtils.hasText(request.message())) {
            throw new AgentException("AGENT_CHAT_MESSAGE_REQUIRED", "chat message is required");
        }
        String sessionId = StringUtils.hasText(request.sessionId())
                ? request.sessionId().trim()
                : trimToNull(request.threadId());
        return ChatInvocation.web(request.message(),
                principal == null ? null : principal.userId(),
                principal == null ? null : principal.username(),
                sessionId, trimToNull(request.memorySpaceId()));
    }

    @Override
    public ChatInvocation requireExternal(ChatInvocation invocation) {
        if (invocation == null
                || invocation.source() != ChatSource.EXTERNAL_IM
                || invocation.ownerUserId() == null
                || !StringUtils.hasText(invocation.message())
                || !StringUtils.hasText(invocation.memorySpaceId())
                || invocation.platform() == null
                || !StringUtils.hasText(invocation.connectorId())
                || !StringUtils.hasText(invocation.conversationId())
                || invocation.conversationType() == null
                || !StringUtils.hasText(invocation.audienceKey())
                || invocation.sender() == null
                || invocation.messageType() == null
                || !StringUtils.hasText(invocation.eventId())) {
            throw new ExternalChatException("EXTERNAL_CHAT_INVOCATION_INVALID",
                    "external chat invocation is incomplete");
        }
        return invocation;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
