package top.egon.mario.agent.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.dto.request.AgentConversationAuditQuery;
import top.egon.mario.agent.dto.response.AgentConversationAuditResponse;
import top.egon.mario.agent.dto.response.AgentConversationMessageAuditResponse;
import top.egon.mario.agent.po.AgentConversationAuditPo;
import top.egon.mario.agent.po.AgentConversationMessageAuditPo;
import top.egon.mario.agent.po.enums.AgentConversationMessageType;
import top.egon.mario.agent.po.enums.AgentConversationRole;
import top.egon.mario.agent.po.enums.AgentConversationStatus;
import top.egon.mario.agent.repository.AgentConversationAuditRepository;
import top.egon.mario.agent.repository.AgentConversationMessageAuditRepository;
import top.egon.mario.agent.service.AgentConversationAuditService;
import top.egon.mario.agent.service.AgentException;
import top.egon.mario.agent.service.model.AgentConversationAuditStart;
import top.egon.mario.agent.service.model.AgentConversationMessageRecord;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Default persistence service for agent conversation audit records.
 */
@Service
@RequiredArgsConstructor
public class AgentConversationAuditServiceImpl implements AgentConversationAuditService {

    private static final int ERROR_MESSAGE_MAX_LENGTH = 1024;
    private static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";

    private final AgentConversationAuditRepository auditRepository;
    private final AgentConversationMessageAuditRepository messageAuditRepository;

    @Override
    @Transactional
    public Long start(AgentConversationAuditStart start, String userMessage) {
        Instant startedAt = start.startedAt() == null ? Instant.now() : start.startedAt();
        AgentConversationAuditPo audit = new AgentConversationAuditPo();
        audit.setRequestId(start.requestId());
        audit.setTraceId(start.traceId());
        audit.setUserId(start.userId());
        audit.setUsername(start.username());
        audit.setThreadId(start.threadId());
        audit.setPresetId(start.presetId());
        audit.setRuntimeFingerprint(start.runtimeFingerprint());
        audit.setEffectiveConfigJson(start.effectiveConfigJson());
        audit.setStatus(AgentConversationStatus.RUNNING);
        audit.setStartedAt(startedAt);
        audit.setIp(start.ip());
        audit.setUserAgent(start.userAgent());
        audit.setCreatedAt(Instant.now());
        AgentConversationAuditPo saved = auditRepository.save(audit);
        messageAuditRepository.save(toMessagePo(saved.getId(), 0,
                new AgentConversationMessageRecord(AgentConversationRole.USER, AgentConversationMessageType.MESSAGE, userMessage)));
        return saved.getId();
    }

    @Override
    @Transactional
    public void complete(Long auditId, List<AgentConversationMessageRecord> messages, Instant finishedAt) {
        AgentConversationAuditPo audit = getAudit(auditId);
        Instant endedAt = finishedAt == null ? Instant.now() : finishedAt;
        audit.setStatus(AgentConversationStatus.SUCCESS);
        audit.setFinishedAt(endedAt);
        audit.setDurationMs(durationMs(audit.getStartedAt(), endedAt));
        if (messages != null && !messages.isEmpty()) {
            messageAuditRepository.saveAll(toMessageRows(auditId, messages));
        }
        auditRepository.save(audit);
    }

    @Override
    @Transactional
    public void fail(Long auditId, String errorCode, String errorMessage, Instant finishedAt) {
        AgentConversationAuditPo audit = getAudit(auditId);
        Instant endedAt = finishedAt == null ? Instant.now() : finishedAt;
        audit.setStatus(AgentConversationStatus.FAILED);
        audit.setFinishedAt(endedAt);
        audit.setDurationMs(durationMs(audit.getStartedAt(), endedAt));
        audit.setErrorCode(errorCode);
        audit.setErrorMessage(truncate(errorMessage, ERROR_MESSAGE_MAX_LENGTH));
        auditRepository.save(audit);
    }

    @Override
    @Transactional
    public void cancel(Long auditId, Instant finishedAt) {
        AgentConversationAuditPo audit = getAudit(auditId);
        Instant endedAt = finishedAt == null ? Instant.now() : finishedAt;
        audit.setStatus(AgentConversationStatus.CANCELLED);
        audit.setFinishedAt(endedAt);
        audit.setDurationMs(durationMs(audit.getStartedAt(), endedAt));
        auditRepository.save(audit);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AgentConversationAuditResponse> page(AgentConversationAuditQuery query, Pageable pageable,
                                                     RbacPrincipal principal) {
        requireSuperAdmin(principal);
        return auditRepository.findAll(specification(query), pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentConversationMessageAuditResponse> messages(Long auditId, RbacPrincipal principal) {
        requireSuperAdmin(principal);
        if (!auditRepository.existsById(auditId)) {
            throw new AgentException("AGENT_CONVERSATION_AUDIT_NOT_FOUND", "agent conversation audit not found");
        }
        return messageAuditRepository.findByConversationAuditIdOrderBySeqNoAsc(auditId).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    private Specification<AgentConversationAuditPo> specification(AgentConversationAuditQuery query) {
        AgentConversationAuditQuery safeQuery = query == null
                ? new AgentConversationAuditQuery(null, null, null, null, null, null, null)
                : query;
        return (root, ignored, cb) -> {
            java.util.ArrayList<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (safeQuery.startAt() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startedAt"), safeQuery.startAt()));
            }
            if (safeQuery.endAt() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startedAt"), safeQuery.endAt()));
            }
            if (safeQuery.userId() != null) {
                predicates.add(cb.equal(root.get("userId"), safeQuery.userId()));
            }
            if (StringUtils.hasText(safeQuery.username())) {
                predicates.add(cb.like(cb.lower(root.get("username")), "%" + safeQuery.username().trim().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(safeQuery.threadId())) {
                predicates.add(cb.equal(root.get("threadId"), safeQuery.threadId().trim()));
            }
            if (safeQuery.presetId() != null) {
                predicates.add(cb.equal(root.get("presetId"), safeQuery.presetId()));
            }
            if (safeQuery.status() != null) {
                predicates.add(cb.equal(root.get("status"), safeQuery.status()));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private AgentConversationAuditPo getAudit(Long auditId) {
        return auditRepository.findById(auditId)
                .orElseThrow(() -> new IllegalStateException("agent conversation audit not found: " + auditId));
    }

    private List<AgentConversationMessageAuditPo> toMessageRows(Long auditId, List<AgentConversationMessageRecord> messages) {
        java.util.ArrayList<AgentConversationMessageAuditPo> rows = new java.util.ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            rows.add(toMessagePo(auditId, i + 1, messages.get(i)));
        }
        return rows;
    }

    private AgentConversationMessageAuditPo toMessagePo(Long auditId, int seqNo, AgentConversationMessageRecord record) {
        AgentConversationMessageAuditPo po = new AgentConversationMessageAuditPo();
        po.setConversationAuditId(auditId);
        po.setSeqNo(seqNo);
        po.setRole(record.role());
        po.setMessageType(record.messageType());
        po.setContent(record.content());
        po.setContentChars(record.content() == null ? 0 : record.content().length());
        po.setCreatedAt(Instant.now());
        return po;
    }

    private Long durationMs(Instant startedAt, Instant finishedAt) {
        if (startedAt == null || finishedAt == null) {
            return null;
        }
        return Duration.between(startedAt, finishedAt).toMillis();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void requireSuperAdmin(RbacPrincipal principal) {
        if (principal == null || principal.roleCodes() == null || !principal.roleCodes().contains(SUPER_ADMIN_ROLE_CODE)) {
            throw new AgentException("AGENT_CONVERSATION_AUDIT_FORBIDDEN", "agent conversation audits are only available to super administrators");
        }
    }

    private AgentConversationAuditResponse toResponse(AgentConversationAuditPo po) {
        return new AgentConversationAuditResponse(
                po.getId(),
                po.getRequestId(),
                po.getTraceId(),
                po.getUserId(),
                po.getUsername(),
                po.getThreadId(),
                po.getPresetId(),
                po.getRuntimeFingerprint(),
                po.getEffectiveConfigJson(),
                po.getStatus(),
                po.getStartedAt(),
                po.getFinishedAt(),
                po.getDurationMs(),
                po.getErrorCode(),
                po.getErrorMessage(),
                po.getIp(),
                po.getUserAgent(),
                po.getCreatedAt()
        );
    }

    private AgentConversationMessageAuditResponse toMessageResponse(AgentConversationMessageAuditPo po) {
        return new AgentConversationMessageAuditResponse(
                po.getId(),
                po.getConversationAuditId(),
                po.getSeqNo(),
                po.getRole(),
                po.getMessageType(),
                po.getContent(),
                po.getContentChars(),
                po.getCreatedAt()
        );
    }

}
