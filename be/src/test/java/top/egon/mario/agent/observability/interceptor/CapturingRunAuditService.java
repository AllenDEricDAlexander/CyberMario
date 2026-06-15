package top.egon.mario.agent.observability.interceptor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import top.egon.mario.agent.observability.dto.request.AgentRunAuditQuery;
import top.egon.mario.agent.observability.dto.response.AgentRunAuditResponse;
import top.egon.mario.agent.observability.dto.response.AgentRunEventAuditResponse;
import top.egon.mario.agent.observability.service.AgentRunAuditService;
import top.egon.mario.agent.observability.service.model.AgentRunAuditContext;
import top.egon.mario.agent.observability.service.model.AgentRunAuditStart;
import top.egon.mario.agent.observability.service.model.AgentRunEventRecord;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class CapturingRunAuditService implements AgentRunAuditService {

    final List<AgentRunEventRecord> events = new ArrayList<>();

    @Override
    public AgentRunAuditContext start(AgentRunAuditStart start) {
        return null;
    }

    @Override
    public void record(AgentRunAuditContext context, AgentRunEventRecord event) {
        events.add(event);
    }

    @Override
    public void complete(AgentRunAuditContext context, String finalMessage, String finalThinking, Instant finishedAt) {
    }

    @Override
    public void fail(AgentRunAuditContext context, String errorCode, String errorMessage, Instant finishedAt) {
    }

    @Override
    public void cancel(AgentRunAuditContext context, Instant finishedAt) {
    }

    @Override
    public Page<AgentRunAuditResponse> page(AgentRunAuditQuery query, Pageable pageable, RbacPrincipal principal) {
        return Page.empty();
    }

    @Override
    public AgentRunAuditResponse detail(Long runId, RbacPrincipal principal) {
        return null;
    }

    @Override
    public List<AgentRunEventAuditResponse> events(Long runId, RbacPrincipal principal) {
        return List.of();
    }
}
