package top.egon.mario.agent.observability.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.observability.dto.request.AgentRunAuditQuery;
import top.egon.mario.agent.observability.dto.response.AgentRunAuditResponse;
import top.egon.mario.agent.observability.dto.response.AgentRunEventAuditResponse;
import top.egon.mario.agent.observability.po.AgentRunAuditPo;
import top.egon.mario.agent.observability.po.AgentRunEventAuditPo;
import top.egon.mario.agent.observability.po.enums.AgentRunAuditStatus;
import top.egon.mario.agent.observability.po.enums.AgentRunEventStatus;
import top.egon.mario.agent.observability.po.enums.AgentRunEventType;
import top.egon.mario.agent.observability.po.enums.AgentRunToolType;
import top.egon.mario.agent.observability.repository.AgentRunAuditRepository;
import top.egon.mario.agent.observability.repository.AgentRunEventAuditRepository;
import top.egon.mario.agent.observability.service.AgentRunAuditService;
import top.egon.mario.agent.observability.service.model.AgentRunAuditContext;
import top.egon.mario.agent.observability.service.model.AgentRunAuditStart;
import top.egon.mario.agent.observability.service.model.AgentRunEventRecord;
import top.egon.mario.agent.service.AgentException;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default persistence service for unified agent run audit timelines.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentRunAuditServiceImpl implements AgentRunAuditService {

    private static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";

    private final AgentRunAuditRepository runRepository;
    private final AgentRunEventAuditRepository eventRepository;
    @SuppressWarnings("unused")
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public AgentRunAuditContext start(AgentRunAuditStart start) {
        Instant startedAt = start.startedAt() == null ? Instant.now() : start.startedAt();
        AgentRunAuditPo run = new AgentRunAuditPo();
        run.setRequestId(start.requestId());
        run.setTraceId(start.traceId());
        run.setThreadId(start.threadId());
        run.setUserId(start.userId());
        run.setUsername(start.username());
        run.setPresetId(start.presetId());
        run.setRuntimeFingerprint(start.runtimeFingerprint());
        run.setEffectiveConfigJson(start.effectiveConfigJson());
        run.setUserMessage(start.userMessage());
        run.setStatus(AgentRunAuditStatus.RUNNING);
        run.setStartedAt(startedAt);
        run.setCreatedAt(Instant.now());
        AgentRunAuditPo saved = runRepository.save(run);
        AgentRunAuditContext context = new AgentRunAuditContext(saved.getId(), start.requestId(), start.traceId(),
                start.userId(), start.username(), start.threadId(), start.presetId(), start.runtimeFingerprint(),
                new AtomicInteger(-1), new AtomicInteger(0),
                start.toolDescriptors() == null ? Map.of() : Map.copyOf(start.toolDescriptors()));
        recordInCurrentTransaction(context, AgentRunEventRecord.builder(AgentRunEventType.RUN_STARTED)
                .status(AgentRunEventStatus.SUCCESS)
                .metadataJson(start.effectiveConfigJson())
                .startedAt(startedAt)
                .finishedAt(startedAt)
                .durationMs(0L)
                .build());
        recordInCurrentTransaction(context, AgentRunEventRecord.builder(AgentRunEventType.USER_MESSAGE)
                .status(AgentRunEventStatus.SUCCESS)
                .responseText(start.userMessage())
                .startedAt(startedAt)
                .finishedAt(startedAt)
                .durationMs(0L)
                .build());
        return context;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AgentRunAuditContext context, AgentRunEventRecord event) {
        recordInCurrentTransaction(context, event);
    }

    @Override
    @Transactional
    public void complete(AgentRunAuditContext context, String finalMessage, String finalThinking, Instant finishedAt) {
        if (context == null || !context.markFinished()) {
            return;
        }
        Instant endedAt = finishedAt == null ? Instant.now() : finishedAt;
        finish(context, AgentRunAuditStatus.SUCCESS, finalMessage, finalThinking, null, null, endedAt);
        if (StringUtils.hasText(finalThinking)) {
            recordInCurrentTransaction(context, AgentRunEventRecord.builder(AgentRunEventType.ASSISTANT_THINK)
                    .responseText(finalThinking)
                    .startedAt(endedAt)
                    .finishedAt(endedAt)
                    .durationMs(0L)
                    .build());
        }
        if (StringUtils.hasText(finalMessage)) {
            recordInCurrentTransaction(context, AgentRunEventRecord.builder(AgentRunEventType.ASSISTANT_MESSAGE)
                    .responseText(finalMessage)
                    .startedAt(endedAt)
                    .finishedAt(endedAt)
                    .durationMs(0L)
                    .build());
        }
        recordInCurrentTransaction(context, AgentRunEventRecord.builder(AgentRunEventType.RUN_COMPLETED)
                .responseText(finalMessage)
                .startedAt(endedAt)
                .finishedAt(endedAt)
                .durationMs(0L)
                .build());
    }

    @Override
    @Transactional
    public void fail(AgentRunAuditContext context, String errorCode, String errorMessage, Instant finishedAt) {
        if (context == null || !context.markFinished()) {
            return;
        }
        Instant endedAt = finishedAt == null ? Instant.now() : finishedAt;
        finish(context, AgentRunAuditStatus.FAILED, null, null, errorCode, errorMessage, endedAt);
        recordInCurrentTransaction(context, AgentRunEventRecord.builder(AgentRunEventType.RUN_FAILED)
                .status(AgentRunEventStatus.FAILED)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .startedAt(endedAt)
                .finishedAt(endedAt)
                .durationMs(0L)
                .build());
    }

    @Override
    @Transactional
    public void cancel(AgentRunAuditContext context, Instant finishedAt) {
        if (context == null || !context.markFinished()) {
            return;
        }
        Instant endedAt = finishedAt == null ? Instant.now() : finishedAt;
        finish(context, AgentRunAuditStatus.CANCELLED, null, null, null, null, endedAt);
        recordInCurrentTransaction(context, AgentRunEventRecord.builder(AgentRunEventType.RUN_CANCELLED)
                .status(AgentRunEventStatus.CANCELLED)
                .startedAt(endedAt)
                .finishedAt(endedAt)
                .durationMs(0L)
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AgentRunAuditResponse> page(AgentRunAuditQuery query, Pageable pageable, RbacPrincipal principal) {
        requireSuperAdmin(principal);
        return runRepository.findAll(specification(query), pageable).map(this::toRunListResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AgentRunAuditResponse detail(Long runId, RbacPrincipal principal) {
        requireSuperAdmin(principal);
        AgentRunAuditPo run = runRepository.findById(runId)
                .orElseThrow(() -> new AgentException("AGENT_RUN_AUDIT_NOT_FOUND", "agent run audit not found"));
        return toRunDetailResponse(run);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentRunEventAuditResponse> events(Long runId, RbacPrincipal principal) {
        requireSuperAdmin(principal);
        if (!runRepository.existsById(runId)) {
            throw new AgentException("AGENT_RUN_AUDIT_NOT_FOUND", "agent run audit not found");
        }
        return eventRepository.findByRunIdOrderBySeqNoAsc(runId).stream().map(this::toEventResponse).toList();
    }

    private void recordInCurrentTransaction(AgentRunAuditContext context, AgentRunEventRecord event) {
        if (context == null || context.runId() == null || event == null) {
            return;
        }
        AgentRunEventAuditPo po = toEventPo(context, event);
        eventRepository.save(po);
        incrementCounters(context.runId(), event);
        logAuditEvent(context, event, po);
    }

    private AgentRunEventAuditPo toEventPo(AgentRunAuditContext context, AgentRunEventRecord event) {
        Instant startedAt = event.startedAt() == null ? Instant.now() : event.startedAt();
        AgentRunEventAuditPo po = new AgentRunEventAuditPo();
        po.setRunId(context.runId());
        po.setRequestId(context.requestId());
        po.setTraceId(context.traceId());
        po.setThreadId(context.threadId());
        po.setSeqNo(context.nextSeq());
        po.setEventType(event.eventType());
        po.setReactRound(event.reactRound());
        po.setToolCallId(event.toolCallId());
        po.setToolName(event.toolName());
        po.setToolType(event.toolType());
        po.setMcpServerCode(event.mcpServerCode());
        po.setStatus(event.status() == null ? AgentRunEventStatus.SUCCESS : event.status());
        po.setStartedAt(startedAt);
        po.setFinishedAt(event.finishedAt());
        po.setDurationMs(event.durationMs());
        po.setModelProvider(event.modelProvider());
        po.setModelName(event.modelName());
        po.setPromptText(event.promptText());
        po.setRequestMessagesJson(event.requestMessagesJson());
        po.setRequestOptionsJson(event.requestOptionsJson());
        po.setAvailableToolsJson(event.availableToolsJson());
        po.setResponseText(event.responseText());
        po.setToolArguments(event.toolArguments());
        po.setToolResult(event.toolResult());
        po.setMetadataJson(event.metadataJson());
        po.setErrorCode(event.errorCode());
        po.setErrorMessage(event.errorMessage());
        po.setCreatedAt(Instant.now());
        return po;
    }

    private void finish(AgentRunAuditContext context, AgentRunAuditStatus status, String finalMessage,
                        String finalThinking, String errorCode, String errorMessage, Instant finishedAt) {
        if (context == null || context.runId() == null) {
            return;
        }
        AgentRunAuditPo run = runRepository.findById(context.runId())
                .orElseThrow(() -> new IllegalStateException("agent run audit not found: " + context.runId()));
        run.setStatus(status);
        run.setFinalMessage(finalMessage);
        run.setFinalThinking(finalThinking);
        run.setFinishedAt(finishedAt);
        run.setDurationMs(durationMs(run.getStartedAt(), finishedAt));
        run.setErrorCode(errorCode);
        run.setErrorMessage(errorMessage);
        runRepository.save(run);
        LogUtil.info(log).log("agent run finished, runId={}, threadId={}, status={}, durationMs={}",
                context.runId(), context.threadId(), status, run.getDurationMs());
    }

    private void incrementCounters(Long runId, AgentRunEventRecord event) {
        if (event.eventType() != AgentRunEventType.MODEL_REQUEST && event.eventType() != AgentRunEventType.TOOL_RESPONSE) {
            return;
        }
        if (event.eventType() == AgentRunEventType.MODEL_REQUEST) {
            runRepository.incrementModelCallCount(runId);
            return;
        }
        runRepository.incrementToolCallCount(runId);
        if (event.toolType() == AgentRunToolType.MCP) {
            runRepository.incrementMcpToolCallCount(runId);
        }
    }

    private Specification<AgentRunAuditPo> specification(AgentRunAuditQuery query) {
        AgentRunAuditQuery safeQuery = query == null
                ? new AgentRunAuditQuery(null, null, null, null, null, null, null, null, null, null, null)
                : query;
        return (root, criteriaQuery, cb) -> {
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
            if (StringUtils.hasText(safeQuery.requestId())) {
                predicates.add(cb.equal(root.get("requestId"), safeQuery.requestId().trim()));
            }
            if (StringUtils.hasText(safeQuery.traceId())) {
                predicates.add(cb.equal(root.get("traceId"), safeQuery.traceId().trim()));
            }
            if (safeQuery.presetId() != null) {
                predicates.add(cb.equal(root.get("presetId"), safeQuery.presetId()));
            }
            if (safeQuery.status() != null) {
                predicates.add(cb.equal(root.get("status"), safeQuery.status()));
            }
            if ((StringUtils.hasText(safeQuery.toolName()) || StringUtils.hasText(safeQuery.mcpServerCode()))
                    && criteriaQuery != null) {
                Subquery<Long> subquery = criteriaQuery.subquery(Long.class);
                var eventRoot = subquery.from(AgentRunEventAuditPo.class);
                java.util.ArrayList<jakarta.persistence.criteria.Predicate> eventPredicates = new java.util.ArrayList<>();
                eventPredicates.add(cb.equal(eventRoot.get("runId"), root.get("id")));
                if (StringUtils.hasText(safeQuery.toolName())) {
                    eventPredicates.add(cb.equal(eventRoot.get("toolName"), safeQuery.toolName().trim()));
                }
                if (StringUtils.hasText(safeQuery.mcpServerCode())) {
                    eventPredicates.add(cb.equal(eventRoot.get("mcpServerCode"), safeQuery.mcpServerCode().trim()));
                }
                subquery.select(eventRoot.get("runId"));
                subquery.where(cb.and(eventPredicates.toArray(new jakarta.persistence.criteria.Predicate[0])));
                predicates.add(cb.exists(subquery));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private AgentRunAuditResponse toRunListResponse(AgentRunAuditPo po) {
        return new AgentRunAuditResponse(po.getId(), po.getRequestId(), po.getTraceId(), po.getThreadId(),
                po.getUserId(), po.getUsername(), po.getPresetId(), po.getRuntimeFingerprint(),
                null, null, null, null,
                po.getStatus(), po.getModelCallCount(), po.getToolCallCount(), po.getMcpToolCallCount(),
                po.getStartedAt(), po.getFinishedAt(), po.getDurationMs(), po.getErrorCode(), po.getErrorMessage(),
                po.getCreatedAt());
    }

    private AgentRunAuditResponse toRunDetailResponse(AgentRunAuditPo po) {
        return new AgentRunAuditResponse(po.getId(), po.getRequestId(), po.getTraceId(), po.getThreadId(),
                po.getUserId(), po.getUsername(), po.getPresetId(), po.getRuntimeFingerprint(),
                po.getEffectiveConfigJson(), po.getUserMessage(), po.getFinalMessage(), po.getFinalThinking(),
                po.getStatus(), po.getModelCallCount(), po.getToolCallCount(), po.getMcpToolCallCount(),
                po.getStartedAt(), po.getFinishedAt(), po.getDurationMs(), po.getErrorCode(), po.getErrorMessage(),
                po.getCreatedAt());
    }

    private AgentRunEventAuditResponse toEventResponse(AgentRunEventAuditPo po) {
        return new AgentRunEventAuditResponse(po.getId(), po.getRunId(), po.getRequestId(), po.getTraceId(),
                po.getThreadId(), po.getSeqNo(), po.getEventType(), po.getReactRound(), po.getToolCallId(),
                po.getToolName(), po.getToolType(), po.getMcpServerCode(), po.getStatus(), po.getStartedAt(),
                po.getFinishedAt(), po.getDurationMs(), po.getModelProvider(), po.getModelName(), po.getPromptText(),
                po.getRequestMessagesJson(), po.getRequestOptionsJson(), po.getAvailableToolsJson(),
                po.getResponseText(), po.getToolArguments(), po.getToolResult(), po.getMetadataJson(),
                po.getErrorCode(), po.getErrorMessage(), po.getCreatedAt());
    }

    private Long durationMs(Instant startedAt, Instant finishedAt) {
        if (startedAt == null || finishedAt == null) {
            return null;
        }
        return Duration.between(startedAt, finishedAt).toMillis();
    }

    private void logAuditEvent(AgentRunAuditContext context, AgentRunEventRecord event, AgentRunEventAuditPo po) {
        LogUtil.info(log).log("agent run audit event saved, runId={}, eventType={}, seqNo={}, status={}, durationMs={}",
                context.runId(), event.eventType(), po.getSeqNo(), po.getStatus(), po.getDurationMs());
        LogUtil.debug(log).log("agent run audit event summary, runId={}, eventType={}, toolName={}, modelName={}, "
                        + "status={}, durationMs={}, promptLength={}, messagesLength={}, optionsLength={}, "
                        + "availableToolsLength={}, responseLength={}, argumentLength={}, resultLength={}, "
                        + "metadataLength={}, errorCode={}",
                context.runId(), event.eventType(), event.toolName(), event.modelName(), po.getStatus(),
                po.getDurationMs(), length(event.promptText()), length(event.requestMessagesJson()),
                length(event.requestOptionsJson()), length(event.availableToolsJson()), length(event.responseText()),
                length(event.toolArguments()), length(event.toolResult()), length(event.metadataJson()),
                event.errorCode());
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private void requireSuperAdmin(RbacPrincipal principal) {
        if (principal == null || principal.roleCodes() == null || !principal.roleCodes().contains(SUPER_ADMIN_ROLE_CODE)) {
            throw new AgentException("AGENT_RUN_AUDIT_FORBIDDEN",
                    "agent run audits are only available to super administrators");
        }
    }
}
