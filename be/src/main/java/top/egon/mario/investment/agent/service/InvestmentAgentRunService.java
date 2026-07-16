package top.egon.mario.investment.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.observability.po.AgentRunAuditPo;
import top.egon.mario.agent.observability.repository.AgentRunAuditRepository;
import top.egon.mario.agent.observability.repository.AgentRunEventAuditRepository;
import top.egon.mario.agent.observability.service.AgentRunAuditService;
import top.egon.mario.agent.observability.service.model.AgentRunAuditContext;
import top.egon.mario.agent.observability.service.model.AgentRunAuditStart;
import top.egon.mario.investment.agent.model.InvestmentAgentAction;
import top.egon.mario.investment.agent.model.InvestmentAgentDecisionProposal;
import top.egon.mario.investment.agent.model.InvestmentAgentExecutionStatus;
import top.egon.mario.investment.agent.model.InvestmentAgentRunInput;
import top.egon.mario.investment.agent.model.InvestmentAgentRunType;
import top.egon.mario.investment.agent.po.InvestmentAgentDecisionPo;
import top.egon.mario.investment.agent.po.InvestmentAgentRunPo;
import top.egon.mario.investment.agent.repository.InvestmentAgentDecisionRepository;
import top.egon.mario.investment.agent.repository.InvestmentAgentRunRepository;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.access.InvestmentAccessService;
import top.egon.mario.investment.common.job.InvestmentJobEnqueueCommand;
import top.egon.mario.investment.common.job.InvestmentJobEnqueueService;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.common.model.InvestmentRunStatus;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.query.InvestmentMarketQueryService;
import top.egon.mario.investment.marketdata.web.dto.InvestmentCandleResponse;
import top.egon.mario.investment.portfolio.po.InvestmentPaperAccountPo;
import top.egon.mario.investment.portfolio.repository.InvestmentPaperAccountRepository;
import top.egon.mario.investment.research.indicator.ResearchHashSupport;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Short transaction boundary for owner-scoped run, audit, and validated-decision state. */
@Service
public class InvestmentAgentRunService {

    private static final int MAX_INSTRUMENTS = 20;

    private final InvestmentAgentRunRepository runRepository;
    private final InvestmentAgentDecisionRepository decisionRepository;
    private final InvestmentAccessService accessService;
    private final InvestmentPaperAccountRepository accountRepository;
    private final InvestmentMarketQueryService marketQueryService;
    private final AgentRunAuditService auditService;
    private final AgentRunAuditRepository auditRepository;
    private final AgentRunEventAuditRepository eventRepository;
    private final InvestmentJobEnqueueService jobEnqueueService;
    private final InvestmentAgentPresetRegistry presetRegistry;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public InvestmentAgentRunService(
            InvestmentAgentRunRepository runRepository,
            InvestmentAgentDecisionRepository decisionRepository,
            InvestmentAccessService accessService,
            InvestmentPaperAccountRepository accountRepository,
            InvestmentMarketQueryService marketQueryService,
            AgentRunAuditService auditService,
            AgentRunAuditRepository auditRepository,
            AgentRunEventAuditRepository eventRepository,
            InvestmentJobEnqueueService jobEnqueueService,
            InvestmentAgentPresetRegistry presetRegistry,
            ObjectMapper objectMapper,
            Clock clock) {
        this.runRepository = runRepository;
        this.decisionRepository = decisionRepository;
        this.accessService = accessService;
        this.accountRepository = accountRepository;
        this.marketQueryService = marketQueryService;
        this.auditService = auditService;
        this.auditRepository = auditRepository;
        this.eventRepository = eventRepository;
        this.jobEnqueueService = jobEnqueueService;
        this.presetRegistry = presetRegistry;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public Submission submit(long actorId, String username, long workspaceId, SubmitCommand command) {
        accessService.requireWorkspaceOwner(workspaceId, actorId);
        ValidatedSubmission validated = validateSubmission(actorId, workspaceId, command);
        InvestmentAgentRunPo existing = runRepository
                .findByIdempotencyKeyAndDeletedFalse(validated.idempotencyKey()).orElse(null);
        if (existing != null) {
            return new Submission(summary(existing), null, true);
        }

        String requestId = UUID.randomUUID().toString();
        Instant startedAt = clock.instant();
        AgentRunAuditContext audit = auditService.start(new AgentRunAuditStart(
                requestId, null, actorId, username, "investment-agent:" + validated.inputHash(), null,
                presetRegistry.runtimeSpec().fingerprint(), presetRegistry.effectiveConfigJson(),
                prompt(validated.input()), presetRegistry.toolDescriptors(), startedAt));

        InvestmentAgentRunPo run = new InvestmentAgentRunPo();
        run.setWorkspaceId(workspaceId);
        run.setAccountId(validated.input().accountId());
        run.setAgentPresetCode(InvestmentAgentPresetRegistry.PRESET_CODE);
        run.setGenericAgentRunAuditId(audit.runId());
        run.setRunType(validated.input().runType());
        run.setStatus(InvestmentRunStatus.PENDING);
        run.setDataAsOf(validated.input().dataAsOf());
        run.setInputSnapshotJson(json(validated.input()));
        run.setStartedAt(startedAt);
        run.setIdempotencyKey(validated.idempotencyKey());
        run.setCreatedBy(actorId);
        run.setUpdatedBy(actorId);
        run = runRepository.saveAndFlush(run);

        long jobId = jobEnqueueService.enqueue(new InvestmentJobEnqueueCommand(
                workspaceId, InvestmentJobType.AGENT_ANALYSIS, 100, startedAt, 3,
                "agent-analysis:" + run.getId(), json(new JobInput(run.getId()))));
        return new Submission(summary(run), jobId, false);
    }

    @Transactional
    public InvestmentAgentRunPo markRunning(long runId) {
        InvestmentAgentRunPo run = requireRun(runId);
        if (run.getStatus() == InvestmentRunStatus.SUCCEEDED || run.getStatus() == InvestmentRunStatus.FAILED) {
            return run;
        }
        run.setStatus(InvestmentRunStatus.RUNNING);
        run.setErrorCode(null);
        run.setErrorMessage(null);
        return runRepository.saveAndFlush(run);
    }

    @Transactional
    public InvestmentAgentDecisionPo persistValidatedDecision(
            long runId, InvestmentAgentDecisionProposal proposal) {
        InvestmentAgentDecisionPo existing = decisionRepository.findFirstByRunIdOrderByIdAsc(runId).orElse(null);
        if (existing != null) {
            return existing;
        }
        InvestmentAgentRunPo run = requireRun(runId);
        if (!Objects.equals(run.getDataAsOf(), proposal.dataAsOf())) {
            throw invalid("Validated decision cutoff does not match its run");
        }
        boolean executable = run.getRunType() == InvestmentAgentRunType.AUTO_TRADE
                && proposal.action() != InvestmentAgentAction.HOLD;
        InvestmentAgentDecisionPo decision = new InvestmentAgentDecisionPo();
        decision.setRunId(runId);
        decision.setInstrumentId(proposal.instrumentId());
        decision.setAction(proposal.action());
        decision.setConfidence(proposal.confidence());
        decision.setHorizon(proposal.horizon());
        decision.setThesis(proposal.thesis());
        decision.setRisksJson(json(proposal.risks()));
        decision.setInvalidationJson(json(proposal.invalidation()));
        decision.setRequestedQuantity(proposal.requestedQuantity());
        decision.setRequestedNotional(proposal.requestedNotional());
        decision.setRequestedLeverage(proposal.requestedLeverage());
        decision.setOrderType(proposal.orderType());
        decision.setLimitPrice(proposal.limitPrice());
        decision.setExecutionStatus(executable
                ? InvestmentAgentExecutionStatus.PENDING
                : InvestmentAgentExecutionStatus.NOT_APPLICABLE);
        decision.setExecutionIdempotencyKey(executable
                ? "agent-exec:" + ResearchHashSupport.sha256(
                runId + "|" + proposal.instrumentId() + "|" + proposal.action())
                : null);
        decision.setDataAsOf(proposal.dataAsOf());
        decision.setExpiresAt(proposal.expiresAt());
        decision.setStatus("VALIDATED");
        decision.setCreatedAt(clock.instant());
        return decisionRepository.saveAndFlush(decision);
    }

    @Transactional
    public InvestmentAgentDecisionPo linkIntent(long decisionId, long intentId) {
        InvestmentAgentDecisionPo decision = decisionRepository.findByIdForUpdate(decisionId)
                .orElseThrow(() -> notFound("Investment Agent decision does not exist"));
        if (decision.getIntentId() != null) {
            if (!decision.getIntentId().equals(intentId)) {
                throw new InvestmentException(InvestmentErrorCode.CONFLICT,
                        "Investment Agent decision is already linked to another intent");
            }
            return decision;
        }
        if (decision.getExecutionStatus() != InvestmentAgentExecutionStatus.PENDING) {
            throw new InvestmentException(InvestmentErrorCode.CONFLICT,
                    "Only a pending Investment Agent decision can link an intent");
        }
        decision.setIntentId(intentId);
        decision.setExecutionStatus(InvestmentAgentExecutionStatus.SUBMITTED);
        return decisionRepository.saveAndFlush(decision);
    }

    @Transactional
    public InvestmentAgentDecisionPo markDecisionFailed(long decisionId) {
        InvestmentAgentDecisionPo decision = decisionRepository.findByIdForUpdate(decisionId)
                .orElseThrow(() -> notFound("Investment Agent decision does not exist"));
        if (decision.getExecutionStatus() == InvestmentAgentExecutionStatus.PENDING) {
            decision.setExecutionStatus(InvestmentAgentExecutionStatus.FAILED);
            return decisionRepository.saveAndFlush(decision);
        }
        return decision;
    }

    @Transactional
    public void markSucceeded(long runId, Long reportId) {
        InvestmentAgentRunPo run = requireRun(runId);
        run.setStatus(InvestmentRunStatus.SUCCEEDED);
        run.setFinishedAt(clock.instant());
        run.setReportId(reportId);
        run.setErrorCode(null);
        run.setErrorMessage(null);
        runRepository.saveAndFlush(run);
    }

    @Transactional
    public void markFailed(long runId, String errorCode, String errorMessage) {
        InvestmentAgentRunPo run = requireRun(runId);
        run.setStatus(InvestmentRunStatus.FAILED);
        run.setFinishedAt(clock.instant());
        run.setErrorCode(truncate(errorCode, 256, "INVESTMENT_AGENT_FAILED"));
        run.setErrorMessage(truncate(errorMessage, 2_000, "Investment Agent execution failed"));
        runRepository.saveAndFlush(run);
    }

    @Transactional(readOnly = true)
    public InvestmentAgentRunPo requireRun(long runId) {
        return runRepository.findByIdAndDeletedFalse(runId)
                .orElseThrow(() -> notFound("Investment Agent run does not exist"));
    }

    @Transactional(readOnly = true)
    public InvestmentAgentRunInput input(long runId) {
        InvestmentAgentRunPo run = requireRun(runId);
        InvestmentAgentRunInput input;
        try {
            input = objectMapper.readValue(run.getInputSnapshotJson(), InvestmentAgentRunInput.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Persisted Investment Agent run input is invalid", exception);
        }
        if (input.actorId() <= 0 || input.workspaceId() != run.getWorkspaceId()
                || !Objects.equals(input.actorId(), run.getCreatedBy())
                || !Objects.equals(input.accountId(), run.getAccountId())
                || input.runType() != run.getRunType()
                || !input.dataAsOf().equals(run.getDataAsOf())) {
            throw new IllegalStateException("Persisted Investment Agent run scope does not match its owner row");
        }
        return input;
    }

    @Transactional(readOnly = true)
    public AgentRunAuditContext auditContext(long runId) {
        InvestmentAgentRunPo run = requireRun(runId);
        AgentRunAuditPo audit = auditRepository.findById(run.getGenericAgentRunAuditId())
                .orElseThrow(() -> new IllegalStateException("Generic Agent audit does not exist"));
        int sequence = eventRepository.findByRunIdOrderBySeqNoAsc(audit.getId()).stream()
                .mapToInt(value -> value.getSeqNo()).max().orElse(-1);
        return new AgentRunAuditContext(audit.getId(), audit.getRequestId(), audit.getTraceId(),
                audit.getUserId(), audit.getUsername(), audit.getThreadId(), audit.getPresetId(),
                audit.getRuntimeFingerprint(), new AtomicInteger(sequence), new AtomicInteger(0),
                presetRegistry.toolDescriptors());
    }

    @Transactional(readOnly = true)
    public InvestmentAgentDecisionPo decision(long runId) {
        return decisionRepository.findFirstByRunIdOrderByIdAsc(runId).orElse(null);
    }

    @Transactional(readOnly = true)
    public InvestmentAgentDecisionPo requireDecision(long decisionId) {
        return decisionRepository.findById(decisionId)
                .orElseThrow(() -> notFound("Investment Agent decision does not exist"));
    }

    @Transactional(readOnly = true)
    public Page<RunSummary> listOwned(long actorId, long workspaceId, Pageable pageable) {
        accessService.requireWorkspaceOwner(workspaceId, actorId);
        return runRepository.findOwnedRuns(workspaceId, actorId, pageable).map(InvestmentAgentRunService::summary);
    }

    @Transactional(readOnly = true)
    public RunDetail ownedDetail(long actorId, long runId) {
        accessService.requireAgentRunOwner(runId, actorId);
        InvestmentAgentRunPo run = runRepository.findOwnedRun(runId, actorId)
                .orElseThrow(InvestmentAgentRunService::forbidden);
        List<DecisionSummary> decisions = decisionRepository.findByRunIdOrderByIdAsc(runId).stream()
                .map(this::decisionSummary).toList();
        return new RunDetail(summary(run), decisions);
    }

    public String prompt(InvestmentAgentRunInput input) {
        return """
                Analyze this server-bound paper-only Investment task.
                preset=%s
                runType=%s
                instrumentIds=%s
                accountPresent=%s
                dataAsOf=%s
                Return the strict JSON object required by the system prompt.
                """.formatted(InvestmentAgentPresetRegistry.PRESET_CODE, input.runType(), input.instrumentIds(),
                input.accountId() != null, input.dataAsOf());
    }

    private ValidatedSubmission validateSubmission(long actorId, long workspaceId, SubmitCommand command) {
        if (command == null || command.runType() == null) {
            throw invalid("Investment Agent runType is required");
        }
        List<Long> instrumentIds = command.instrumentIds() == null ? List.of() : command.instrumentIds().stream()
                .filter(Objects::nonNull).distinct().sorted().toList();
        if (instrumentIds.size() > MAX_INSTRUMENTS
                || (command.runType() != InvestmentAgentRunType.PORTFOLIO_REVIEW && instrumentIds.isEmpty())
                || instrumentIds.stream().anyMatch(value -> value <= 0)) {
            throw invalid("Investment Agent instrument scope is invalid");
        }
        instrumentIds.forEach(marketQueryService::instrument);
        Long accountId = command.accountId();
        if (command.runType() == InvestmentAgentRunType.AUTO_TRADE && accountId == null) {
            throw invalid("AUTO_TRADE requires a paper account");
        }
        if (accountId != null) {
            InvestmentPaperAccountPo account = accountRepository.findOwnedAccount(accountId, actorId)
                    .orElseThrow(InvestmentAgentRunService::forbidden);
            if (!Objects.equals(account.getWorkspaceId(), workspaceId)) {
                throw forbidden();
            }
        }
        Instant dataAsOf = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        requireFreshClosedData(instrumentIds, dataAsOf);
        InvestmentAgentRunInput input = new InvestmentAgentRunInput(
                actorId, workspaceId, accountId, command.runType(), instrumentIds, dataAsOf);
        String canonical = workspaceId + "|" + accountId + "|" + InvestmentAgentPresetRegistry.PRESET_CODE
                + "|" + command.runType() + "|" + instrumentIds + "|" + dataAsOf;
        String inputHash = ResearchHashSupport.sha256(canonical);
        return new ValidatedSubmission(input, inputHash, "investment-agent:" + inputHash);
    }

    private void requireFreshClosedData(List<Long> instrumentIds, Instant dataAsOf) {
        for (Long instrumentId : instrumentIds) {
            List<InvestmentCandleResponse> candles = marketQueryService.candles(
                    instrumentId, PriceType.MARK, BarInterval.M1, dataAsOf.minus(Duration.ofHours(6)),
                    dataAsOf, dataAsOf, 360);
            InvestmentCandleResponse latest = candles.stream()
                    .filter(InvestmentCandleResponse::isClosed)
                    .filter(candle -> candle.closeTime() != null && !candle.closeTime().isAfter(dataAsOf))
                    .filter(candle -> candle.dataAsOf() != null && !candle.dataAsOf().isAfter(dataAsOf))
                    .max(java.util.Comparator.comparing(InvestmentCandleResponse::closeTime))
                    .orElseThrow(() -> new InvestmentException(InvestmentErrorCode.DATA_UNAVAILABLE,
                            "No validated closed mark candle is available for instrument " + instrumentId));
            if (Duration.between(latest.closeTime(), dataAsOf).compareTo(Duration.ofMinutes(5)) > 0) {
                throw new InvestmentException(InvestmentErrorCode.DATA_UNAVAILABLE,
                        "Validated mark candle is stale for instrument " + instrumentId);
            }
        }
    }

    private DecisionSummary decisionSummary(InvestmentAgentDecisionPo value) {
        return new DecisionSummary(value.getId(), value.getInstrumentId(), value.getAction(), value.getConfidence(),
                value.getHorizon(), value.getThesis(), stringList(value.getRisksJson()),
                stringList(value.getInvalidationJson()), value.getRequestedQuantity(), value.getRequestedNotional(),
                value.getRequestedLeverage(), value.getOrderType(), value.getLimitPrice(), value.getIntentId(),
                value.getExecutionStatus(), value.getDataAsOf(), value.getExpiresAt(), value.getStatus(),
                value.getCreatedAt());
    }

    private List<String> stringList(String json) {
        try {
            return objectMapper.readerForListOf(String.class).readValue(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Persisted Investment Agent decision JSON is invalid", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize Investment Agent state", exception);
        }
    }

    private static RunSummary summary(InvestmentAgentRunPo value) {
        return new RunSummary(value.getId(), value.getWorkspaceId(), value.getAccountId(),
                value.getAgentPresetCode(), value.getGenericAgentRunAuditId(), value.getRunType(), value.getStatus(),
                value.getDataAsOf(), value.getReportId(), value.getStartedAt(), value.getFinishedAt(),
                value.getErrorCode(), value.getErrorMessage(), value.getCreatedAt());
    }

    private static String truncate(String value, int maxLength, String fallback) {
        String normalized = StringUtils.hasText(value) ? value : fallback;
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private static InvestmentException forbidden() {
        return new InvestmentException(InvestmentErrorCode.FORBIDDEN,
                "Investment Agent private resource access denied");
    }

    private static InvestmentException invalid(String message) {
        return new InvestmentException(InvestmentErrorCode.INVALID_REQUEST, message);
    }

    private static InvestmentException notFound(String message) {
        return new InvestmentException(InvestmentErrorCode.NOT_FOUND, message);
    }

    public record SubmitCommand(InvestmentAgentRunType runType, Long accountId, List<Long> instrumentIds) {
    }

    public record JobInput(long runId) {
    }

    public record Submission(RunSummary run, Long jobId, boolean duplicate) {
    }

    public record RunSummary(
            Long id,
            Long workspaceId,
            Long accountId,
            String presetCode,
            Long genericAgentRunAuditId,
            InvestmentAgentRunType runType,
            InvestmentRunStatus status,
            Instant dataAsOf,
            Long reportId,
            Instant startedAt,
            Instant finishedAt,
            String errorCode,
            String errorMessage,
            Instant createdAt
    ) {
    }

    public record DecisionSummary(
            Long id,
            Long instrumentId,
            InvestmentAgentAction action,
            java.math.BigDecimal confidence,
            String horizon,
            String thesis,
            List<String> risks,
            List<String> invalidation,
            java.math.BigDecimal requestedQuantity,
            java.math.BigDecimal requestedNotional,
            java.math.BigDecimal requestedLeverage,
            top.egon.mario.investment.common.model.OrderType orderType,
            java.math.BigDecimal limitPrice,
            Long intentId,
            InvestmentAgentExecutionStatus executionStatus,
            Instant dataAsOf,
            Instant expiresAt,
            String status,
            Instant createdAt
    ) {
    }

    public record RunDetail(RunSummary run, List<DecisionSummary> decisions) {
        public RunDetail {
            decisions = List.copyOf(decisions);
        }
    }

    private record ValidatedSubmission(
            InvestmentAgentRunInput input, String inputHash, String idempotencyKey) {
    }
}
