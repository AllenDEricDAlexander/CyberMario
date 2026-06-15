package top.egon.mario.agent.observability.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import top.egon.mario.agent.observability.dto.request.AgentRunAuditQuery;
import top.egon.mario.agent.observability.dto.response.AgentRunAuditResponse;
import top.egon.mario.agent.observability.dto.response.AgentRunEventAuditResponse;
import top.egon.mario.agent.observability.service.model.AgentRunAuditContext;
import top.egon.mario.agent.observability.service.model.AgentRunAuditStart;
import top.egon.mario.agent.observability.service.model.AgentRunEventRecord;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.List;

/**
 * Persists complete agent run timeline audits for super administrators.
 */
public interface AgentRunAuditService {

    AgentRunAuditContext start(AgentRunAuditStart start);

    void record(AgentRunAuditContext context, AgentRunEventRecord event);

    void complete(AgentRunAuditContext context, String finalMessage, String finalThinking, Instant finishedAt);

    void fail(AgentRunAuditContext context, String errorCode, String errorMessage, Instant finishedAt);

    void cancel(AgentRunAuditContext context, Instant finishedAt);

    Page<AgentRunAuditResponse> page(AgentRunAuditQuery query, Pageable pageable, RbacPrincipal principal);

    AgentRunAuditResponse detail(Long runId, RbacPrincipal principal);

    List<AgentRunEventAuditResponse> events(Long runId, RbacPrincipal principal);
}
