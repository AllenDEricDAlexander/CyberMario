package top.egon.mario.investment.agent.service;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.agent.model.dto.enums.ModelScenario;
import top.egon.mario.agent.model.service.model.ModelCallContext;
import top.egon.mario.agent.observability.service.AgentRunAuditService;
import top.egon.mario.agent.observability.service.model.AgentRunAuditContext;
import top.egon.mario.agent.service.AgentRuntimeFactory;
import top.egon.mario.investment.agent.model.InvestmentAgentDecisionProposal;
import top.egon.mario.investment.agent.model.InvestmentAgentExecutionStatus;
import top.egon.mario.investment.agent.model.InvestmentAgentRunInput;
import top.egon.mario.investment.agent.po.InvestmentAgentDecisionPo;
import top.egon.mario.investment.agent.po.InvestmentAgentRunPo;
import top.egon.mario.investment.agent.tool.InvestmentAgentToolScope;
import top.egon.mario.investment.common.model.InvestmentRunStatus;
import top.egon.mario.investment.research.report.InvestmentReportService;

import java.time.Clock;
import java.util.UUID;

/** Transaction-free orchestration around one scoped, audited model call and one validated execution path. */
@Service
public class InvestmentAgentRunner {

    private final InvestmentAgentRunService runService;
    private final InvestmentAgentPresetRegistry presetRegistry;
    private final InvestmentAgentToolCallbackFactory toolCallbackFactory;
    private final InvestmentAgentDecisionValidator decisionValidator;
    private final InvestmentAgentDecisionExecutionService executionService;
    private final AgentRuntimeFactory runtimeFactory;
    private final AgentRunAuditService auditService;
    private final InvestmentReportService reportService;
    private final Clock clock;

    public InvestmentAgentRunner(
            InvestmentAgentRunService runService,
            InvestmentAgentPresetRegistry presetRegistry,
            InvestmentAgentToolCallbackFactory toolCallbackFactory,
            InvestmentAgentDecisionValidator decisionValidator,
            InvestmentAgentDecisionExecutionService executionService,
            AgentRuntimeFactory runtimeFactory,
            AgentRunAuditService auditService,
            InvestmentReportService reportService,
            Clock clock) {
        this.runService = runService;
        this.presetRegistry = presetRegistry;
        this.toolCallbackFactory = toolCallbackFactory;
        this.decisionValidator = decisionValidator;
        this.executionService = executionService;
        this.runtimeFactory = runtimeFactory;
        this.auditService = auditService;
        this.reportService = reportService;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.NEVER)
    public RunOutcome run(long runId) {
        InvestmentAgentRunPo run = runService.markRunning(runId);
        if (run.getStatus() == InvestmentRunStatus.SUCCEEDED) {
            InvestmentAgentDecisionPo decision = runService.decision(runId);
            return new RunOutcome(runId, decision == null ? null : decision.getId(), run.getReportId());
        }
        InvestmentAgentRunInput input = runService.input(runId);
        AgentRunAuditContext auditContext = runService.auditContext(runId);
        try {
            InvestmentAgentDecisionPo decision = runService.decision(runId);
            if (decision == null) {
                decision = callAndPersist(runId, input, auditContext);
            }
            if (decision.getExecutionStatus() == InvestmentAgentExecutionStatus.PENDING) {
                executionService.execute(decision.getId());
                decision = runService.requireDecision(decision.getId());
            }
            if (decision.getExecutionStatus() == InvestmentAgentExecutionStatus.FAILED) {
                throw new IllegalStateException("Investment Agent decision execution failed");
            }
            Long reportId = reportService.createAgentAnalysis(
                    input.actorId(), input.workspaceId(), runId, decision.getInstrumentId(), input.dataAsOf())
                    .report().reportId();
            runService.markSucceeded(runId, reportId);
            auditService.complete(auditContext,
                    "Validated Investment decision " + decision.getId() + " completed", null, clock.instant());
            return new RunOutcome(runId, decision.getId(), reportId);
        } catch (RuntimeException exception) {
            auditService.fail(auditContext, exception.getClass().getName(), exception.getMessage(), clock.instant());
            throw exception;
        } catch (Exception exception) {
            auditService.fail(auditContext, exception.getClass().getName(), exception.getMessage(), clock.instant());
            throw new IllegalStateException("Investment Agent model execution failed", exception);
        }
    }

    private InvestmentAgentDecisionPo callAndPersist(long runId, InvestmentAgentRunInput input,
                                                      AgentRunAuditContext auditContext) throws Exception {
        var scopedTools = toolCallbackFactory.create(new InvestmentAgentToolScope(
                input.actorId(), input.workspaceId(), input.accountId(), input.instrumentIds(), input.dataAsOf()));
        String requestId = auditContext.requestId() == null ? UUID.randomUUID().toString() : auditContext.requestId();
        ModelCallContext modelContext = new ModelCallContext(
                input.actorId(), auditContext.traceId(), null, auditContext.threadId(),
                ModelScenario.INVESTMENT_AGENT, requestId, null, null);
        AgentRuntimeFactory.AgentRuntime runtime = runtimeFactory.runtime(
                presetRegistry.runtimeSpec(), modelContext, scopedTools);
        RunnableConfig.Builder config = RunnableConfig.builder().threadId(auditContext.threadId());
        auditContext.metadata().forEach(config::addMetadata);
        String finalOutput = runtime.agent().call(runService.prompt(input), config.build()).getText();
        InvestmentAgentDecisionProposal proposal = decisionValidator.validate(finalOutput, input);
        return runService.persistValidatedDecision(runId, proposal);
    }

    public record RunOutcome(long runId, Long decisionId, Long reportId) {
    }
}
