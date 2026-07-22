package top.egon.mario.agent.externalim.memory.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.externalim.memory.ExternalImMemoryExtractionService;
import top.egon.mario.agent.externalim.memory.model.ExternalImMemoryExtractionRequest;
import top.egon.mario.agent.memory.po.AgentLongTermMemoryPo;
import top.egon.mario.agent.memory.po.AgentMemoryExtractionAuditPo;
import top.egon.mario.agent.memory.po.AgentMemoryMessagePo;
import top.egon.mario.agent.memory.po.AgentMemorySessionPo;
import top.egon.mario.agent.memory.po.enums.AgentLongTermMemoryScopeType;
import top.egon.mario.agent.memory.po.enums.AgentMemoryDomain;
import top.egon.mario.agent.memory.po.enums.AgentMemoryExtractionStatus;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageRole;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageStatus;
import top.egon.mario.agent.memory.repository.AgentMemoryExtractionAuditRepository;
import top.egon.mario.agent.memory.service.AgentLongTermMemoryService;
import top.egon.mario.agent.memory.service.AgentMemoryDefaults;
import top.egon.mario.agent.memory.service.model.AgentLongTermMemoryMergeRequest;

import java.time.Instant;
import java.util.Set;

@Service
public class DefaultExternalImMemoryExtractionService
        implements ExternalImMemoryExtractionService {

    private static final Set<String> MARKERS = Set.of("记住", "偏好", "喜欢", "以后", "默认", "不要");

    private final AgentLongTermMemoryService longTermMemoryService;
    private final AgentMemoryExtractionAuditRepository auditRepository;

    public DefaultExternalImMemoryExtractionService(
            AgentLongTermMemoryService longTermMemoryService,
            AgentMemoryExtractionAuditRepository auditRepository) {
        this.longTermMemoryService = longTermMemoryService;
        this.auditRepository = auditRepository;
    }

    @Override
    @Transactional
    public void extractAfterReply(ExternalImMemoryExtractionRequest request) {
        if (!eligible(request)) {
            return;
        }
        AgentMemorySessionPo session = request.session();
        AgentMemoryMessagePo user = request.userMessage();
        AgentMemoryMessagePo assistant = request.assistantMessage();
        AgentLongTermMemoryPo current = longTermMemoryService.getOrCreate(
                session.getUserId(), session.getUsername(), AgentLongTermMemoryScopeType.IM_SHARED,
                session.getMemorySpaceId());
        String base = StringUtils.hasText(current.getContentMarkdown())
                ? current.getContentMarkdown().trim()
                : AgentMemoryDefaults.DEFAULT_USER_MEMORY_MARKDOWN.trim();
        String mergedMarkdown = base + "\n\n## IM Shared Preferences\n- 用户表达: "
                + user.getContent().trim() + " [source: IM_SHARED space="
                + session.getMemorySpaceId() + " event=" + user.getExternalEventId() + "]";
        AgentMemoryExtractionAuditPo audit = audit(session, request);
        audit.setSourceMessageIds(user.getId() + "," + assistant.getId());
        try {
            AgentLongTermMemoryPo merged = longTermMemoryService.merge(new AgentLongTermMemoryMergeRequest(
                    session.getUserId(), session.getUsername(), AgentLongTermMemoryScopeType.IM_SHARED,
                    mergedMarkdown, "auto extract memory from external IM reply",
                    session.getSessionId(), audit.getSourceMessageIds(), request.requestId(),
                    request.traceId(), session.getMemorySpaceId()));
            audit.setStatus(AgentMemoryExtractionStatus.SUCCESS);
            audit.setExtractedMarkdown(mergedMarkdown);
            audit.setMergedVersionId(merged.getActiveVersionId());
            audit.setFinishedAt(Instant.now());
            auditRepository.save(audit);
        } catch (RuntimeException error) {
            audit.setStatus(AgentMemoryExtractionStatus.FAILED);
            audit.setErrorCode(error.getClass().getName());
            audit.setErrorMessage(error.getMessage());
            audit.setFinishedAt(Instant.now());
            auditRepository.save(audit);
            throw error;
        }
    }

    private boolean eligible(ExternalImMemoryExtractionRequest request) {
        if (request == null || request.session() == null || request.userMessage() == null
                || request.assistantMessage() == null) {
            return false;
        }
        AgentMemorySessionPo session = request.session();
        AgentMemoryMessagePo user = request.userMessage();
        AgentMemoryMessagePo assistant = request.assistantMessage();
        return session.getMemoryDomain() == AgentMemoryDomain.IM_SHARED
                && StringUtils.hasText(session.getMemorySpaceId())
                && user.getRole() == AgentMemoryMessageRole.USER
                && assistant.getRole() == AgentMemoryMessageRole.ASSISTANT
                && user.getMessageStatus() == AgentMemoryMessageStatus.SUCCEEDED
                && assistant.getMessageStatus() == AgentMemoryMessageStatus.SUCCEEDED
                && StringUtils.hasText(user.getContent())
                && MARKERS.stream().anyMatch(user.getContent()::contains);
    }

    private AgentMemoryExtractionAuditPo audit(
            AgentMemorySessionPo session,
            ExternalImMemoryExtractionRequest request) {
        Instant now = Instant.now();
        AgentMemoryExtractionAuditPo audit = new AgentMemoryExtractionAuditPo();
        audit.setUserId(session.getUserId());
        audit.setSessionId(session.getSessionId());
        audit.setEntryType(session.getEntryType());
        audit.setMemorySpaceId(session.getMemorySpaceId());
        audit.setStatus(AgentMemoryExtractionStatus.RUNNING);
        audit.setRequestId(request.requestId());
        audit.setTraceId(request.traceId());
        audit.setStartedAt(now);
        audit.setCreatedAt(now);
        return audit;
    }
}
