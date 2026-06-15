package top.egon.mario.agent.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import top.egon.mario.agent.dto.request.AgentConversationAuditQuery;
import top.egon.mario.agent.dto.response.AgentConversationAuditResponse;
import top.egon.mario.agent.dto.response.AgentConversationMessageAuditResponse;
import top.egon.mario.agent.service.model.AgentConversationAuditStart;
import top.egon.mario.agent.service.model.AgentConversationMessageRecord;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.List;

/**
 * Persists complete agent conversation history for super-admin auditing.
 */
public interface AgentConversationAuditService {

    /**
     * Starts an audit record and stores the initial user message.
     */
    Long start(AgentConversationAuditStart start, String userMessage);

    /**
     * Marks the conversation as successful and appends final assistant messages.
     */
    void complete(Long auditId, List<AgentConversationMessageRecord> messages, Instant finishedAt);

    /**
     * Marks the conversation as failed.
     */
    void fail(Long auditId, String errorCode, String errorMessage, Instant finishedAt);

    /**
     * Marks the conversation as cancelled by the client.
     */
    void cancel(Long auditId, Instant finishedAt);

    /**
     * Returns super-admin-only conversation audit rows.
     */
    Page<AgentConversationAuditResponse> page(AgentConversationAuditQuery query, Pageable pageable, RbacPrincipal principal);

    /**
     * Returns super-admin-only original conversation message rows.
     */
    List<AgentConversationMessageAuditResponse> messages(Long auditId, RbacPrincipal principal);

}
