package top.egon.mario.agent.externalim.guard.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.agent.externalim.guard.ChatGuardAuditService;
import top.egon.mario.agent.externalim.guard.ChatGuardResult;
import top.egon.mario.agent.externalim.guard.po.AgentChatGuardAuditPo;
import top.egon.mario.agent.externalim.guard.repository.AgentChatGuardAuditRepository;
import top.egon.mario.agent.externalim.model.ChatInvocation;
import top.egon.mario.agent.externalim.model.ChatSource;

import java.time.Instant;

@Service
public class DefaultChatGuardAuditService implements ChatGuardAuditService {

    private final AgentChatGuardAuditRepository repository;

    public DefaultChatGuardAuditService(AgentChatGuardAuditRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void record(ChatInvocation invocation, ChatGuardResult result,
                       String requestId, String traceId) {
        AgentChatGuardAuditPo audit = new AgentChatGuardAuditPo();
        audit.setOwnerUserId(invocation == null ? null : invocation.ownerUserId());
        audit.setChatSource(invocation == null || invocation.source() == null
                ? ChatSource.EXTERNAL_IM : invocation.source());
        audit.setMemorySpaceId(invocation == null ? null : invocation.memorySpaceId());
        audit.setPlatform(invocation == null ? null : invocation.platform());
        audit.setConnectorId(invocation == null ? null : invocation.connectorId());
        audit.setConversationId(invocation == null ? null : invocation.conversationId());
        audit.setConversationType(invocation == null ? null : invocation.conversationType());
        audit.setAudienceKey(invocation == null ? null : invocation.audienceKey());
        audit.setDecision(result.decision());
        audit.setConfidence(result.confidence());
        audit.setReason(result.reason());
        audit.setModelProvider(result.modelProvider());
        audit.setModelName(result.modelName());
        audit.setDurationMs(result.durationMs());
        audit.setRequestId(requestId);
        audit.setTraceId(traceId);
        audit.setExternalEventId(invocation == null ? null : invocation.eventId());
        audit.setCreatedAt(Instant.now());
        repository.save(audit);
    }
}
