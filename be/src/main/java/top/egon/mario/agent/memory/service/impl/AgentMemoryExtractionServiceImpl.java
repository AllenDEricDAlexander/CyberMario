package top.egon.mario.agent.memory.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.memory.po.AgentMemoryExtractionAuditPo;
import top.egon.mario.agent.memory.po.AgentMemoryMessagePo;
import top.egon.mario.agent.memory.po.AgentMemorySessionPo;
import top.egon.mario.agent.memory.po.enums.AgentLongTermMemoryScopeType;
import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemoryExtractionStatus;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageRole;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageType;
import top.egon.mario.agent.memory.po.enums.AgentMemorySessionStatus;
import top.egon.mario.agent.memory.repository.AgentMemoryExtractionAuditRepository;
import top.egon.mario.agent.memory.repository.AgentMemoryMessageRepository;
import top.egon.mario.agent.memory.repository.AgentMemorySessionRepository;
import top.egon.mario.agent.memory.service.AgentLongTermMemoryService;
import top.egon.mario.agent.memory.service.AgentMemoryDefaults;
import top.egon.mario.agent.memory.service.AgentMemoryExtractionService;
import top.egon.mario.agent.memory.service.AgentMemoryException;
import top.egon.mario.agent.memory.service.model.AgentLongTermMemoryMergeRequest;
import top.egon.mario.agent.memory.service.model.AgentMemoryExtractionRequest;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic first-pass extractor for explicit user memory markers.
 */
@Service
public class AgentMemoryExtractionServiceImpl implements AgentMemoryExtractionService {

    private static final Set<String> MARKERS = Set.of("记住", "偏好", "喜欢", "以后", "默认", "不要");

    private final AgentMemorySessionRepository sessionRepository;
    private final AgentMemoryMessageRepository messageRepository;
    private final AgentMemoryExtractionAuditRepository auditRepository;
    private final AgentLongTermMemoryService longTermMemoryService;

    public AgentMemoryExtractionServiceImpl(AgentMemorySessionRepository sessionRepository,
                                            AgentMemoryMessageRepository messageRepository,
                                            AgentMemoryExtractionAuditRepository auditRepository,
                                            AgentLongTermMemoryService longTermMemoryService) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.auditRepository = auditRepository;
        this.longTermMemoryService = longTermMemoryService;
    }

    @Override
    @Transactional
    public void extractAfterTurn(AgentMemoryExtractionRequest request) {
        if (request == null || !StringUtils.hasText(request.sessionId())) {
            return;
        }
        AgentMemorySessionPo session = sessionRepository.findBySessionIdAndDeletedFalse(request.sessionId())
                .orElseThrow(() -> new AgentMemoryException("AGENT_MEMORY_SESSION_NOT_FOUND",
                        "memory session not found"));
        AgentMemoryExtractionAuditPo audit = audit(session, request);
        try {
            if (session.getStatus() == AgentMemorySessionStatus.ARCHIVED
                    || session.getStatus() == AgentMemorySessionStatus.DELETED
                    || session.isDeleted()) {
                skip(audit, "AGENT_MEMORY_SESSION_NOT_ACTIVE", "memory session is not active");
                return;
            }
            if (!session.isLongTermExtractionEnabled()) {
                skip(audit, "AGENT_MEMORY_EXTRACTION_DISABLED", "memory extraction is disabled");
                return;
            }
            List<AgentMemoryMessagePo> userMessages = messageRepository
                    .findBySessionIdAndDeletedFalseOrderBySeqNoAsc(session.getSessionId()).stream()
                    .filter(message -> message.getRole() == AgentMemoryMessageRole.USER)
                    .filter(message -> message.getMessageType() == AgentMemoryMessageType.MESSAGE)
                    .filter(message -> StringUtils.hasText(message.getContent()))
                    .filter(message -> hasMarker(message.getContent()))
                    .toList();
            if (userMessages.isEmpty()) {
                skip(audit, "AGENT_MEMORY_NO_EXTRACTABLE_MARKER", "no extractable memory marker");
                return;
            }
            var currentMemory = longTermMemoryService.getOrCreate(session.getUserId(), session.getUsername(),
                    AgentLongTermMemoryScopeType.USER_AGENT);
            String baseMarkdown = currentMemory == null
                    ? AgentMemoryDefaults.DEFAULT_USER_MEMORY_MARKDOWN
                    : currentMemory.getContentMarkdown();
            String mergedMarkdown = appendBullets(baseMarkdown,
                    sectionName(session.getEntryType(), userMessages.getLast().getContent()),
                    userMessages.stream()
                            .map(message -> bullet(session, message))
                            .toList());
            audit.setSourceMessageIds(userMessages.stream()
                    .map(AgentMemoryMessagePo::getId)
                    .map(String::valueOf)
                    .collect(Collectors.joining(",")));
            var merged = longTermMemoryService.merge(new AgentLongTermMemoryMergeRequest(
                    session.getUserId(),
                    session.getUsername(),
                    AgentLongTermMemoryScopeType.USER_AGENT,
                    mergedMarkdown,
                    "auto extract memory from " + session.getEntryType(),
                    session.getSessionId(),
                    audit.getSourceMessageIds(),
                    request.requestId(),
                    request.traceId()
            ));
            audit.setStatus(AgentMemoryExtractionStatus.SUCCESS);
            audit.setExtractedMarkdown(mergedMarkdown);
            audit.setMergedVersionId(merged.getActiveVersionId());
            audit.setFinishedAt(Instant.now());
            auditRepository.save(audit);
        } catch (RuntimeException ex) {
            audit.setStatus(AgentMemoryExtractionStatus.FAILED);
            audit.setErrorCode(ex.getClass().getName());
            audit.setErrorMessage(ex.getMessage());
            audit.setFinishedAt(Instant.now());
            auditRepository.save(audit);
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentMemoryExtractionAuditPo> userAudits(RbacPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new AgentMemoryException("AGENT_MEMORY_UNAUTHENTICATED", "memory requires an authenticated user");
        }
        return auditRepository.findByUserIdOrderByCreatedAtDesc(principal.userId());
    }

    private AgentMemoryExtractionAuditPo audit(AgentMemorySessionPo session, AgentMemoryExtractionRequest request) {
        Instant now = Instant.now();
        AgentMemoryExtractionAuditPo audit = new AgentMemoryExtractionAuditPo();
        audit.setUserId(session.getUserId());
        audit.setSessionId(session.getSessionId());
        audit.setEntryType(session.getEntryType());
        audit.setStatus(AgentMemoryExtractionStatus.RUNNING);
        audit.setRequestId(request.requestId());
        audit.setTraceId(request.traceId());
        audit.setStartedAt(now);
        audit.setCreatedAt(now);
        return audit;
    }

    private void skip(AgentMemoryExtractionAuditPo audit, String code, String message) {
        audit.setStatus(AgentMemoryExtractionStatus.SKIPPED);
        audit.setErrorCode(code);
        audit.setErrorMessage(message);
        audit.setFinishedAt(Instant.now());
        auditRepository.save(audit);
    }

    private boolean hasMarker(String content) {
        return MARKERS.stream().anyMatch(content::contains);
    }

    private String sectionName(AgentMemoryEntryType entryType, String content) {
        if (entryType == AgentMemoryEntryType.RAG_CHAT) {
            return "RAG-Derived Notes";
        }
        if (content.contains("喜欢") || content.contains("偏好")) {
            return "Preferences";
        }
        return "Do Not Forget";
    }

    private String bullet(AgentMemorySessionPo session, AgentMemoryMessagePo message) {
        return "- 用户表达: %s [source: %s session=%s]"
                .formatted(message.getContent().trim(), session.getEntryType(), session.getSessionId());
    }

    private String appendBullets(String markdown, String sectionName, List<String> bullets) {
        String safeMarkdown = StringUtils.hasText(markdown)
                ? markdown.trim()
                : AgentMemoryDefaults.DEFAULT_USER_MEMORY_MARKDOWN.trim();
        String heading = "## " + sectionName;
        String bulletText = String.join("\n", bullets);
        int sectionIndex = safeMarkdown.indexOf(heading);
        if (sectionIndex < 0) {
            return safeMarkdown + "\n\n" + heading + "\n" + bulletText;
        }
        int insertAt = safeMarkdown.indexOf("\n## ", sectionIndex + heading.length());
        if (insertAt < 0) {
            return safeMarkdown + "\n" + bulletText;
        }
        return safeMarkdown.substring(0, insertAt).trim() + "\n" + bulletText + "\n\n"
                + safeMarkdown.substring(insertAt).trim();
    }
}
