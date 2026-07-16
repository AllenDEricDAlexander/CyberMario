package top.egon.mario.investment.agent;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import top.egon.mario.agent.observability.service.AgentRunAuditService;
import top.egon.mario.agent.observability.service.model.AgentRunAuditContext;
import top.egon.mario.agent.service.AgentRuntimeFactory;
import top.egon.mario.agent.service.model.ScopedAgentToolSet;
import top.egon.mario.investment.agent.model.InvestmentAgentAction;
import top.egon.mario.investment.agent.model.InvestmentAgentExecutionStatus;
import top.egon.mario.investment.agent.model.InvestmentAgentRunInput;
import top.egon.mario.investment.agent.model.InvestmentAgentRunType;
import top.egon.mario.investment.agent.po.InvestmentAgentDecisionPo;
import top.egon.mario.investment.agent.po.InvestmentAgentRunPo;
import top.egon.mario.investment.agent.service.InvestmentAgentDecisionExecutionService;
import top.egon.mario.investment.agent.service.InvestmentAgentDecisionValidator;
import top.egon.mario.investment.agent.service.InvestmentAgentPresetRegistry;
import top.egon.mario.investment.agent.service.InvestmentAgentRunService;
import top.egon.mario.investment.agent.service.InvestmentAgentRunner;
import top.egon.mario.investment.agent.service.InvestmentAgentToolCallbackFactory;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.model.InvestmentRunStatus;
import top.egon.mario.investment.research.report.InvestmentReportService;
import top.egon.mario.investment.research.web.dto.CreateInvestmentReportResponse;
import top.egon.mario.investment.research.web.dto.InvestmentReportSummaryResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestmentAgentRunnerTests {

    private static final Instant CUTOFF = Instant.parse("2035-01-01T00:00:00Z");

    @Mock
    private InvestmentAgentRunService runService;
    @Mock
    private InvestmentAgentPresetRegistry presetRegistry;
    @Mock
    private InvestmentAgentToolCallbackFactory toolCallbackFactory;
    @Mock
    private InvestmentAgentDecisionExecutionService executionService;
    @Mock
    private AgentRuntimeFactory runtimeFactory;
    @Mock
    private AgentRunAuditService auditService;
    @Mock
    private InvestmentReportService reportService;
    @Mock
    private ReactAgent reactAgent;

    private InvestmentAgentRunner runner;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(CUTOFF.plusSeconds(30), ZoneOffset.UTC);
        runner = new InvestmentAgentRunner(
                runService, presetRegistry, toolCallbackFactory,
                new InvestmentAgentDecisionValidator(clock), executionService,
                runtimeFactory, auditService, reportService, clock);
    }

    @Test
    void holdIsValidatedPersistedAuditedAndNeverEntersTrading() throws Exception {
        InvestmentAgentRunInput input = input(InvestmentAgentRunType.INSTRUMENT_ANALYSIS, null);
        InvestmentAgentDecisionPo decision = decision(InvestmentAgentAction.HOLD,
                InvestmentAgentExecutionStatus.NOT_APPLICABLE);
        arrangeModelRun(input, holdJson(), decision);

        InvestmentAgentRunner.RunOutcome outcome = runner.run(41L);

        assertThat(outcome).isEqualTo(new InvestmentAgentRunner.RunOutcome(41L, 51L, 91L));
        verify(runService).persistValidatedDecision(org.mockito.ArgumentMatchers.eq(41L), any());
        verify(executionService, never()).execute(org.mockito.ArgumentMatchers.anyLong());
        verify(runService).markSucceeded(41L, 91L);
        verify(auditService).complete(any(), anyString(), org.mockito.ArgumentMatchers.isNull(), any());
    }

    @Test
    void analysisRecommendationDoesNotExecuteEvenWhenActionIsOpen() throws Exception {
        InvestmentAgentRunInput input = input(InvestmentAgentRunType.INSTRUMENT_ANALYSIS, null);
        InvestmentAgentDecisionPo decision = decision(InvestmentAgentAction.OPEN_LONG,
                InvestmentAgentExecutionStatus.NOT_APPLICABLE);
        arrangeModelRun(input, openJson(), decision);

        runner.run(41L);

        verify(executionService, never()).execute(org.mockito.ArgumentMatchers.anyLong());
        verify(runService).markSucceeded(41L, 91L);
    }

    @Test
    void autoTradeUsesOnlyThePostValidationExecutionService() throws Exception {
        InvestmentAgentRunInput input = input(InvestmentAgentRunType.AUTO_TRADE, 31L);
        InvestmentAgentDecisionPo pending = decision(InvestmentAgentAction.OPEN_LONG,
                InvestmentAgentExecutionStatus.PENDING);
        InvestmentAgentDecisionPo submitted = decision(InvestmentAgentAction.OPEN_LONG,
                InvestmentAgentExecutionStatus.SUBMITTED);
        arrangeModelRun(input, openJson(), pending);
        when(runService.requireDecision(51L)).thenReturn(submitted);

        runner.run(41L);

        verify(executionService).execute(51L);
        verify(runService).requireDecision(51L);
        verify(runService).markSucceeded(41L, 91L);
    }

    @Test
    void invalidFinalOutputAfterReadPhaseCreatesNoDecisionIntentOrReport() throws Exception {
        InvestmentAgentRunInput input = input(InvestmentAgentRunType.AUTO_TRADE, 31L);
        arrangeBase(input);
        when(reactAgent.call(anyString(), any(RunnableConfig.class)))
                .thenReturn(new AssistantMessage("{\"action\":\"OPEN_LONG\"}"));

        assertThatThrownBy(() -> runner.run(41L)).isInstanceOf(InvestmentException.class);

        verify(runService, never()).persistValidatedDecision(org.mockito.ArgumentMatchers.anyLong(), any());
        verify(executionService, never()).execute(org.mockito.ArgumentMatchers.anyLong());
        verify(reportService, never()).createAgentAnalysis(any(), any(), any(), any(), any());
        verify(auditService).fail(any(), anyString(), anyString(), any());
    }

    @Test
    void modelTimeoutAfterReadToolsCreatesNoDecisionOrTradingSideEffect() throws Exception {
        InvestmentAgentRunInput input = input(InvestmentAgentRunType.AUTO_TRADE, 31L);
        arrangeBase(input);
        when(reactAgent.call(anyString(), any(RunnableConfig.class)))
                .thenThrow(new IllegalStateException("model timeout"));

        assertThatThrownBy(() -> runner.run(41L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("model timeout");

        verify(runService, never()).persistValidatedDecision(org.mockito.ArgumentMatchers.anyLong(), any());
        verify(executionService, never()).execute(org.mockito.ArgumentMatchers.anyLong());
        verify(reportService, never()).createAgentAnalysis(any(), any(), any(), any(), any());
        verify(auditService).fail(any(), anyString(), org.mockito.ArgumentMatchers.contains("timeout"), any());
    }

    @Test
    void strictSchemaRejectsArraysUnknownFieldsEnumsRangesAndExpiredDecisions() {
        InvestmentAgentDecisionValidator validator = new InvestmentAgentDecisionValidator(
                Clock.fixed(CUTOFF.plusSeconds(30), ZoneOffset.UTC));
        InvestmentAgentRunInput input = input(InvestmentAgentRunType.AUTO_TRADE, 31L);

        assertThatThrownBy(() -> validator.validate("[]", input)).isInstanceOf(InvestmentException.class);
        assertThatThrownBy(() -> validator.validate(
                openJson().replace("\"expiresAt\":null", "\"expiresAt\":null,\"workspaceId\":7"), input))
                .hasMessageContaining("unknown field");
        assertThatThrownBy(() -> validator.validate(
                openJson().replace("OPEN_LONG", "BUY_NOW"), input))
                .hasMessageContaining("unsupported value");
        assertThatThrownBy(() -> validator.validate(
                openJson().replace("\"instrumentId\":11", "\"instrumentId\":11.5"), input))
                .hasMessageContaining("positive integer");
        assertThatThrownBy(() -> validator.validate(
                openJson().replace("\"0.75\"", "\"1.01\""), input))
                .hasMessageContaining("between zero and one");
        assertThatThrownBy(() -> validator.validate(
                openJson().replace("\"expiresAt\":null",
                        "\"expiresAt\":\"2035-01-01T00:00:10Z\""), input))
                .hasMessageContaining("outside the supported decision window");
    }

    private void arrangeModelRun(InvestmentAgentRunInput input, String output,
                                 InvestmentAgentDecisionPo decision) throws Exception {
        arrangeBase(input);
        when(reactAgent.call(anyString(), any(RunnableConfig.class)))
                .thenReturn(new AssistantMessage(output));
        when(runService.persistValidatedDecision(org.mockito.ArgumentMatchers.eq(41L), any()))
                .thenReturn(decision);
        InvestmentReportSummaryResponse report = new InvestmentReportSummaryResponse(
                91L, 7L, 11L, "AGENT_ANALYSIS", "Agent", "", "PENDING", 1L, CUTOFF, CUTOFF);
        when(reportService.createAgentAnalysis(5L, 7L, 41L, 11L, CUTOFF))
                .thenReturn(new CreateInvestmentReportResponse(report, 92L));
    }

    private void arrangeBase(InvestmentAgentRunInput input) {
        InvestmentAgentRunPo run = new InvestmentAgentRunPo();
        run.setId(41L);
        run.setStatus(InvestmentRunStatus.RUNNING);
        when(runService.markRunning(41L)).thenReturn(run);
        when(runService.input(41L)).thenReturn(input);
        when(runService.auditContext(41L)).thenReturn(auditContext());
        when(runService.decision(41L)).thenReturn(null);
        when(runService.prompt(input)).thenReturn("analyze");
        when(toolCallbackFactory.create(any())).thenReturn(ScopedAgentToolSet.empty());
        when(runtimeFactory.runtime(any(), any(), any()))
                .thenReturn(new AgentRuntimeFactory.AgentRuntime(reactAgent, Map.of()));
    }

    private static InvestmentAgentRunInput input(InvestmentAgentRunType type, Long accountId) {
        return new InvestmentAgentRunInput(5L, 7L, accountId, type, List.of(11L), CUTOFF);
    }

    private static InvestmentAgentDecisionPo decision(
            InvestmentAgentAction action, InvestmentAgentExecutionStatus executionStatus) {
        InvestmentAgentDecisionPo decision = new InvestmentAgentDecisionPo();
        decision.setId(51L);
        decision.setInstrumentId(11L);
        decision.setAction(action);
        decision.setExecutionStatus(executionStatus);
        return decision;
    }

    private static AgentRunAuditContext auditContext() {
        return new AgentRunAuditContext(61L, "request-1", "trace-1", 5L, "owner",
                "investment-agent:test", null, "fingerprint", new AtomicInteger(1),
                new AtomicInteger(0), Map.of());
    }

    private static String holdJson() {
        return """
                {"instrumentId":11,"action":"HOLD","confidence":"0.60","horizon":"INTRADAY",
                 "thesis":"wait","risks":["volatility"],"invalidation":["breakout"],
                 "requestedQuantity":null,"requestedNotional":null,"requestedLeverage":null,
                 "orderType":null,"limitPrice":null,"dataAsOf":"2035-01-01T00:00:00Z","expiresAt":null}
                """;
    }

    private static String openJson() {
        return """
                {"instrumentId":11,"action":"OPEN_LONG","confidence":"0.75","horizon":"INTRADAY",
                 "thesis":"trend","risks":["volatility"],"invalidation":["support lost"],
                 "requestedQuantity":"1","requestedNotional":"100","requestedLeverage":"2",
                 "orderType":"MARKET","limitPrice":null,"dataAsOf":"2035-01-01T00:00:00Z","expiresAt":null}
                """;
    }
}
