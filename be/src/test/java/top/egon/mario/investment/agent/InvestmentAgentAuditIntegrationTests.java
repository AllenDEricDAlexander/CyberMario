package top.egon.mario.investment.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.egon.mario.agent.observability.po.AgentRunAuditPo;
import top.egon.mario.agent.observability.po.AgentRunEventAuditPo;
import top.egon.mario.agent.observability.po.enums.AgentRunToolType;
import top.egon.mario.agent.observability.repository.AgentRunAuditRepository;
import top.egon.mario.agent.observability.repository.AgentRunEventAuditRepository;
import top.egon.mario.agent.observability.service.AgentRunAuditService;
import top.egon.mario.agent.observability.service.model.AgentRunAuditContext;
import top.egon.mario.investment.agent.model.InvestmentAgentAction;
import top.egon.mario.investment.agent.model.InvestmentAgentExecutionStatus;
import top.egon.mario.investment.agent.model.InvestmentAgentRunType;
import top.egon.mario.investment.agent.po.InvestmentAgentDecisionPo;
import top.egon.mario.investment.agent.po.InvestmentAgentRunPo;
import top.egon.mario.investment.agent.report.AgentAnalysisReportGenerator;
import top.egon.mario.investment.agent.repository.InvestmentAgentDecisionRepository;
import top.egon.mario.investment.agent.repository.InvestmentAgentRunRepository;
import top.egon.mario.investment.agent.service.InvestmentAgentPresetRegistry;
import top.egon.mario.investment.agent.service.InvestmentAgentRunService;
import top.egon.mario.investment.common.access.InvestmentAccessService;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.job.InvestmentJobEnqueueService;
import top.egon.mario.investment.common.model.InvestmentRunStatus;
import top.egon.mario.investment.marketdata.query.InvestmentMarketQueryService;
import top.egon.mario.investment.marketdata.web.dto.InvestmentCandleResponse;
import top.egon.mario.investment.portfolio.repository.InvestmentPaperAccountRepository;
import top.egon.mario.investment.research.po.InvestmentResearchReportPo;
import top.egon.mario.investment.research.repository.InvestmentResearchReportRepository;
import top.egon.mario.investment.research.report.FrozenResearchReportInput;
import top.egon.mario.investment.research.report.InvestmentReportType;
import top.egon.mario.investment.research.report.ResearchEvidenceSource;
import top.egon.mario.investment.research.report.ResearchEvidenceSourceService;
import top.egon.mario.investment.research.report.ResearchReportGenerationContext;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvestmentAgentAuditIntegrationTests {

    @Test
    void sortedDeduplicatedInstrumentSetAtOneServerCutoffReusesTheSameRun() {
        Instant now = Instant.parse("2035-01-01T00:00:30Z");
        Instant cutoff = Instant.parse("2035-01-01T00:00:00Z");
        InvestmentAgentRunRepository runRepository = mock(InvestmentAgentRunRepository.class);
        InvestmentAccessService accessService = mock(InvestmentAccessService.class);
        InvestmentMarketQueryService marketQueryService = mock(InvestmentMarketQueryService.class);
        AgentRunAuditService auditService = mock(AgentRunAuditService.class);
        InvestmentJobEnqueueService enqueueService = mock(InvestmentJobEnqueueService.class);
        InvestmentAgentRunPo existing = new InvestmentAgentRunPo();
        existing.setId(41L);
        existing.setWorkspaceId(7L);
        existing.setAgentPresetCode(InvestmentAgentPresetRegistry.PRESET_CODE);
        existing.setRunType(InvestmentAgentRunType.INSTRUMENT_ANALYSIS);
        existing.setStatus(InvestmentRunStatus.PENDING);
        existing.setDataAsOf(cutoff);
        existing.setStartedAt(now);
        when(runRepository.findByIdempotencyKeyAndDeletedFalse(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.of(existing));
        InvestmentCandleResponse candle = new InvestmentCandleResponse(
                cutoff.minusSeconds(60), cutoff, "1", "1", "1", "1", "1", "1", true, 1L, cutoff);
        when(marketQueryService.candles(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(candle));
        InvestmentAgentRunService service = new InvestmentAgentRunService(
                runRepository, mock(InvestmentAgentDecisionRepository.class), accessService,
                mock(InvestmentPaperAccountRepository.class), marketQueryService, auditService,
                mock(AgentRunAuditRepository.class), mock(AgentRunEventAuditRepository.class), enqueueService,
                mock(InvestmentAgentPresetRegistry.class), JsonMapper.builder().findAndAddModules().build(),
                Clock.fixed(now, ZoneOffset.UTC));

        var first = service.submit(5L, "owner", 7L, new InvestmentAgentRunService.SubmitCommand(
                InvestmentAgentRunType.INSTRUMENT_ANALYSIS, null, List.of(12L, 11L, 12L)));
        var second = service.submit(5L, "owner", 7L, new InvestmentAgentRunService.SubmitCommand(
                InvestmentAgentRunType.INSTRUMENT_ANALYSIS, null, List.of(11L, 12L)));

        assertThat(first.duplicate()).isTrue();
        assertThat(second.duplicate()).isTrue();
        assertThat(first.run().id()).isEqualTo(41L);
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(runRepository, org.mockito.Mockito.times(2)).findByIdempotencyKeyAndDeletedFalse(keys.capture());
        assertThat(keys.getAllValues()).containsOnly(keys.getAllValues().getFirst());
        verify(auditService, never()).start(org.mockito.ArgumentMatchers.any());
        verify(enqueueService, never()).enqueue(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void staleOrMissingClosedMarketDataPreventsRunAndGenericAuditCreation() {
        Instant now = Instant.parse("2035-01-01T00:00:30Z");
        InvestmentMarketQueryService marketQueryService = mock(InvestmentMarketQueryService.class);
        AgentRunAuditService auditService = mock(AgentRunAuditService.class);
        InvestmentJobEnqueueService enqueueService = mock(InvestmentJobEnqueueService.class);
        InvestmentAgentRunService service = new InvestmentAgentRunService(
                mock(InvestmentAgentRunRepository.class), mock(InvestmentAgentDecisionRepository.class),
                mock(InvestmentAccessService.class), mock(InvestmentPaperAccountRepository.class),
                marketQueryService, auditService, mock(AgentRunAuditRepository.class),
                mock(AgentRunEventAuditRepository.class), enqueueService,
                mock(InvestmentAgentPresetRegistry.class), JsonMapper.builder().findAndAddModules().build(),
                Clock.fixed(now, ZoneOffset.UTC));
        when(marketQueryService.candles(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.submit(5L, "owner", 7L,
                new InvestmentAgentRunService.SubmitCommand(
                        InvestmentAgentRunType.INSTRUMENT_ANALYSIS, null, List.of(11L))))
                .isInstanceOf(InvestmentException.class)
                .hasMessageContaining("No validated closed mark candle");

        verify(auditService, never()).start(org.mockito.ArgumentMatchers.any());
        verify(enqueueService, never()).enqueue(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resumesTheLinkedGenericAuditAtItsPersistedSequenceWithScopedDescriptors() {
        InvestmentAgentRunRepository runRepository = mock(InvestmentAgentRunRepository.class);
        AgentRunAuditRepository auditRepository = mock(AgentRunAuditRepository.class);
        AgentRunEventAuditRepository eventRepository = mock(AgentRunEventAuditRepository.class);
        InvestmentAgentPresetRegistry presetRegistry = mock(InvestmentAgentPresetRegistry.class);
        InvestmentAgentRunService service = new InvestmentAgentRunService(
                runRepository, mock(InvestmentAgentDecisionRepository.class), mock(InvestmentAccessService.class),
                mock(InvestmentPaperAccountRepository.class), mock(InvestmentMarketQueryService.class),
                mock(AgentRunAuditService.class), auditRepository, eventRepository,
                mock(InvestmentJobEnqueueService.class), presetRegistry,
                JsonMapper.builder().findAndAddModules().build(), Clock.systemUTC());
        InvestmentAgentRunPo run = new InvestmentAgentRunPo();
        run.setId(41L);
        run.setGenericAgentRunAuditId(61L);
        AgentRunAuditPo audit = new AgentRunAuditPo();
        audit.setId(61L);
        audit.setRequestId("request-1");
        audit.setTraceId("trace-1");
        audit.setUserId(5L);
        audit.setUsername("owner");
        audit.setThreadId("investment-agent:test");
        audit.setRuntimeFingerprint("fingerprint");
        AgentRunEventAuditPo first = new AgentRunEventAuditPo();
        first.setSeqNo(0);
        AgentRunEventAuditPo last = new AgentRunEventAuditPo();
        last.setSeqNo(7);
        Map<String, AgentRunAuditContext.ToolDescriptor> descriptors = Map.of(
                "get_investment_portfolio",
                new AgentRunAuditContext.ToolDescriptor(AgentRunToolType.LOCAL, null));
        when(runRepository.findByIdAndDeletedFalse(41L)).thenReturn(Optional.of(run));
        when(auditRepository.findById(61L)).thenReturn(Optional.of(audit));
        when(eventRepository.findByRunIdOrderBySeqNoAsc(61L)).thenReturn(List.of(first, last));
        when(presetRegistry.toolDescriptors()).thenReturn(descriptors);

        AgentRunAuditContext context = service.auditContext(41L);

        assertThat(context.runId()).isEqualTo(61L);
        assertThat(context.userId()).isEqualTo(5L);
        assertThat(context.toolDescriptors()).isEqualTo(descriptors);
        assertThat(context.nextSeq()).isEqualTo(8);
    }

    @Test
    void reportUsesOnlyTheValidatedDecisionAndItsImmutableEvidenceCutoff() {
        Instant cutoff = Instant.parse("2035-01-01T00:00:00Z");
        InvestmentResearchReportRepository reportRepository = mock(InvestmentResearchReportRepository.class);
        InvestmentAgentRunRepository runRepository = mock(InvestmentAgentRunRepository.class);
        InvestmentAgentDecisionRepository decisionRepository = mock(InvestmentAgentDecisionRepository.class);
        ResearchEvidenceSourceService evidenceSourceService = mock(ResearchEvidenceSourceService.class);
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        AgentAnalysisReportGenerator generator = new AgentAnalysisReportGenerator(
                reportRepository, runRepository, decisionRepository, evidenceSourceService, mapper);
        InvestmentResearchReportPo report = new InvestmentResearchReportPo();
        report.setId(91L);
        report.setSourceType("AGENT");
        report.setSourceReferenceId(41L);
        InvestmentAgentRunPo run = new InvestmentAgentRunPo();
        run.setId(41L);
        run.setWorkspaceId(7L);
        run.setRunType(InvestmentAgentRunType.INSTRUMENT_ANALYSIS);
        run.setDataAsOf(cutoff);
        InvestmentAgentDecisionPo decision = new InvestmentAgentDecisionPo();
        decision.setId(51L);
        decision.setInstrumentId(11L);
        decision.setAction(InvestmentAgentAction.OPEN_LONG);
        decision.setConfidence(new BigDecimal("0.75"));
        decision.setHorizon("INTRADAY");
        decision.setThesis("trend <script>alert(1)</script>");
        decision.setRisksJson("[\"volatility\"]");
        decision.setInvalidationJson("[\"support lost\"]");
        decision.setExecutionStatus(InvestmentAgentExecutionStatus.NOT_APPLICABLE);
        decision.setDataAsOf(cutoff);
        when(reportRepository.findByIdAndDeletedFalse(91L)).thenReturn(Optional.of(report));
        when(runRepository.findByIdAndDeletedFalse(41L)).thenReturn(Optional.of(run));
        when(decisionRepository.findByRunIdOrderByIdAsc(41L)).thenReturn(List.of(decision));
        when(evidenceSourceService.requireInstrumentSource(11L))
                .thenReturn(new ResearchEvidenceSource(21L, 11L, "BITGET", "BTCUSDT"));
        FrozenResearchReportInput input = new FrozenResearchReportInput(
                InvestmentReportType.AGENT_ANALYSIS, 11L, null, null, null, null, cutoff);

        var generated = generator.generate(new ResearchReportGenerationContext(
                91L, 7L, 1L, input, "hash"));

        assertThat(generated.contentMarkdown()).contains("OPEN_LONG", cutoff.toString())
                .doesNotContain("<script>");
        assertThat(generated.evidence()).singleElement().satisfies(evidence -> {
            assertThat(evidence.dataAsOf()).isEqualTo(cutoff);
            assertThat(evidence.sourceId()).isEqualTo(21L);
            assertThat(evidence.sourceReference()).isEqualTo("agent-run:41/decision:51");
            assertThat(evidence.payloadHash()).hasSize(64);
        });
    }
}
