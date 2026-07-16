package top.egon.mario.investment.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.access.InvestmentAccessService;
import top.egon.mario.investment.common.job.InvestmentJobClaim;
import top.egon.mario.investment.common.job.InvestmentJobEnqueueCommand;
import top.egon.mario.investment.common.job.InvestmentJobEnqueueService;
import top.egon.mario.investment.common.job.InvestmentJobRetryableException;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.research.po.InvestmentReportEvidencePo;
import top.egon.mario.investment.research.po.InvestmentResearchReportPo;
import top.egon.mario.investment.research.report.FrozenResearchReportInput;
import top.egon.mario.investment.research.report.GeneratedResearchReport;
import top.egon.mario.investment.research.report.InvestmentReportBuildJobHandler;
import top.egon.mario.investment.research.report.InvestmentReportService;
import top.egon.mario.investment.research.report.InvestmentReportType;
import top.egon.mario.investment.research.report.InvestmentResearchReportGenerator;
import top.egon.mario.investment.research.report.InvestmentResearchReportGeneratorRegistry;
import top.egon.mario.investment.research.report.PersistedResearchMetrics;
import top.egon.mario.investment.research.report.PreparedResearchReport;
import top.egon.mario.investment.research.report.ResearchReportEvidenceDraft;
import top.egon.mario.investment.research.report.ResearchReportGenerationContext;
import top.egon.mario.investment.research.repository.InvestmentReportEvidenceRepository;
import top.egon.mario.investment.research.repository.InvestmentResearchReportRepository;
import top.egon.mario.investment.research.web.dto.CreateInvestmentReportRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies queued creation, retry behavior and immutable report/evidence boundaries.
 */
@ExtendWith(MockitoExtension.class)
class InvestmentReportServiceTests {

    private static final Instant NOW = Instant.parse("2030-02-01T00:00:00Z");

    @Mock
    private InvestmentResearchReportRepository reportRepository;
    @Mock
    private InvestmentReportEvidenceRepository evidenceRepository;
    @Mock
    private InvestmentAccessService accessService;
    @Mock
    private InvestmentJobEnqueueService enqueueService;

    private ObjectMapper objectMapper;
    private InvestmentResearchReportGenerator generator;
    private InvestmentResearchReportGeneratorRegistry registry;
    private InvestmentReportService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        generator = mock(InvestmentResearchReportGenerator.class);
        when(generator.reportType()).thenReturn(InvestmentReportType.MARKET_OVERVIEW);
        registry = new InvestmentResearchReportGeneratorRegistry(List.of(generator));
        service = new InvestmentReportService(
                reportRepository, evidenceRepository, accessService, enqueueService, registry,
                objectMapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsOneFrozenPendingVersionAndQueuesTheSharedReportJob() throws Exception {
        when(reportRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            InvestmentResearchReportPo report = invocation.getArgument(0);
            report.setId(31L);
            report.setCreatedAt(NOW);
            return report;
        });
        when(enqueueService.enqueue(any())).thenReturn(71L);

        var response = service.create(101L, 11L,
                new CreateInvestmentReportRequest("MARKET_OVERVIEW", null, null, null, null, null));

        assertThat(response.jobId()).isEqualTo(71L);
        assertThat(response.report().status()).isEqualTo("PENDING");
        assertThat(response.report().reportVersion()).isEqualTo(1L);
        assertThat(response.report().dataAsOf()).isEqualTo(NOW);
        ArgumentCaptor<InvestmentResearchReportPo> reportCaptor =
                ArgumentCaptor.forClass(InvestmentResearchReportPo.class);
        verify(reportRepository).saveAndFlush(reportCaptor.capture());
        PersistedResearchMetrics metrics = objectMapper.readValue(
                reportCaptor.getValue().getMetricsJson(), PersistedResearchMetrics.class);
        assertThat(metrics.input().dataAsOf()).isEqualTo(NOW);
        assertThat(metrics.inputHash()).hasSize(64);
        ArgumentCaptor<InvestmentJobEnqueueCommand> jobCaptor =
                ArgumentCaptor.forClass(InvestmentJobEnqueueCommand.class);
        verify(enqueueService).enqueue(jobCaptor.capture());
        assertThat(jobCaptor.getValue().jobType()).isEqualTo(InvestmentJobType.REPORT_BUILD);
        assertThat(jobCaptor.getValue().idempotencyKey()).isEqualTo("report-build:31:v1");
        assertThat(jobCaptor.getValue().workspaceId()).isEqualTo(11L);
    }

    @Test
    void rejectsAReportTypeWhoseOwningGeneratorIsNotInstalled() {
        InvestmentReportService withoutLaterPhases = new InvestmentReportService(
                reportRepository, evidenceRepository, accessService, enqueueService,
                new InvestmentResearchReportGeneratorRegistry(List.of()), objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> withoutLaterPhases.create(101L, 11L,
                new CreateInvestmentReportRequest("PORTFOLIO_REPORT", null, null, null, null, null)))
                .isInstanceOf(InvestmentException.class)
                .satisfies(error -> assertThat(((InvestmentException) error).getErrorCode())
                        .isEqualTo(InvestmentErrorCode.CAPABILITY_UNAVAILABLE));
        verify(reportRepository, never()).saveAndFlush(any());
        verify(enqueueService, never()).enqueue(any());
    }

    @Test
    void rejectsInstrumentDimensionsForAPortfolioReport() {
        InvestmentResearchReportGenerator portfolioGenerator = mock(InvestmentResearchReportGenerator.class);
        when(portfolioGenerator.reportType()).thenReturn(InvestmentReportType.PORTFOLIO_REPORT);
        InvestmentReportService portfolioService = new InvestmentReportService(
                reportRepository, evidenceRepository, accessService, enqueueService,
                new InvestmentResearchReportGeneratorRegistry(List.of(portfolioGenerator)), objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> portfolioService.create(101L, 11L,
                new CreateInvestmentReportRequest("PORTFOLIO_REPORT", 501L, null, null, null, null)))
                .isInstanceOf(InvestmentException.class)
                .satisfies(error -> assertThat(((InvestmentException) error).getErrorCode())
                        .isEqualTo(InvestmentErrorCode.INVALID_REQUEST));
        verify(reportRepository, never()).saveAndFlush(any());
        verify(enqueueService, never()).enqueue(any());
    }

    @Test
    void completesWithoutChangingVersionCutoffOrEvidenceOwner() {
        FrozenResearchReportInput input = new FrozenResearchReportInput(
                InvestmentReportType.MARKET_OVERVIEW, null, null, null, null, null, NOW);
        String inputHash = top.egon.mario.investment.research.indicator.ResearchHashSupport
                .sha256(input.canonicalValue());
        ResearchReportGenerationContext context = new ResearchReportGenerationContext(31L, 11L, 1L, input, inputHash);
        InvestmentResearchReportPo report = report(31L, 11L, input, inputHash, "PENDING");
        when(reportRepository.findByIdAndDeletedFalse(31L)).thenReturn(Optional.of(report));
        when(reportRepository.saveAndFlush(report)).thenReturn(report);
        ResearchReportEvidenceDraft draft = new ResearchReportEvidenceDraft(
                "MARKET_OVERVIEW", 9L, 42L, NOW, NOW, NOW, "source:9/overview",
                "a".repeat(64), "{\"revision\":1}");
        GeneratedResearchReport generated = new GeneratedResearchReport(
                "Market", "Frozen", "# Market\n\nSafe Markdown", Map.of("count", 2),
                Map.of("latest", "1.25"), List.of(draft));

        service.completeBuild(context, generated);

        assertThat(report.getStatus()).isEqualTo("READY");
        assertThat(report.getReportVersion()).isEqualTo(1L);
        assertThat(report.getDataAsOf()).isEqualTo(NOW);
        ArgumentCaptor<Iterable<InvestmentReportEvidencePo>> evidenceCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(evidenceRepository).saveAll(evidenceCaptor.capture());
        assertThat(evidenceCaptor.getValue()).singleElement().satisfies(evidence -> {
            assertThat(evidence.getReportId()).isEqualTo(31L);
            assertThat(evidence.getSourceId()).isEqualTo(9L);
            assertThat(evidence.getDataAsOf()).isEqualTo(NOW);
            assertThat(evidence.getPayloadHash()).isEqualTo("a".repeat(64));
        });
    }

    @Test
    void rejectsRawHtmlBeforePersistingGeneratedMarkdown() {
        FrozenResearchReportInput input = new FrozenResearchReportInput(
                InvestmentReportType.MARKET_OVERVIEW, null, null, null, null, null, NOW);
        String hash = top.egon.mario.investment.research.indicator.ResearchHashSupport.sha256(input.canonicalValue());
        InvestmentResearchReportPo report = report(31L, 11L, input, hash, "PENDING");
        when(reportRepository.findByIdAndDeletedFalse(31L)).thenReturn(Optional.of(report));
        GeneratedResearchReport generated = new GeneratedResearchReport(
                "Unsafe", "Unsafe", "# Title\n<script>alert(1)</script>", Map.of(), null,
                List.of(new ResearchReportEvidenceDraft("X", 1L, null, NOW, NOW, NOW,
                        "source:1", "b".repeat(64), "{}")));

        assertThatThrownBy(() -> service.completeBuild(
                new ResearchReportGenerationContext(31L, 11L, 1L, input, hash), generated))
                .isInstanceOf(InvestmentException.class)
                .satisfies(error -> assertThat(((InvestmentException) error).getErrorCode())
                        .isEqualTo(InvestmentErrorCode.INVALID_REQUEST));
        verify(reportRepository, never()).saveAndFlush(report);
        verify(evidenceRepository, never()).saveAll(any());
    }

    @Test
    void durableHandlerRetriesAndMarksTheReportFailedAtTheRetryBudget() throws Exception {
        FrozenResearchReportInput input = new FrozenResearchReportInput(
                InvestmentReportType.MARKET_OVERVIEW, null, null, null, null, null, NOW);
        ResearchReportGenerationContext context = new ResearchReportGenerationContext(
                31L, 11L, 1L, input, "c".repeat(64));
        InvestmentReportService reportService = mock(InvestmentReportService.class);
        when(reportService.prepareBuild(31L, 1L)).thenReturn(new PreparedResearchReport(context, "PENDING"));
        when(generator.generate(context)).thenThrow(new IllegalStateException("temporary market failure"));
        InvestmentReportBuildJobHandler handler = new InvestmentReportBuildJobHandler(
                reportService, registry, objectMapper);
        String jobInput = objectMapper.writeValueAsString(Map.of("reportId", 31, "reportVersion", 1));

        assertThatThrownBy(() -> handler.execute(claim(jobInput, 0, 3)))
                .isInstanceOf(InvestmentJobRetryableException.class);
        verify(reportService, never()).failBuild(any(Long.class), any(String.class));

        assertThatThrownBy(() -> handler.execute(claim(jobInput, 2, 3)))
                .isInstanceOf(InvestmentJobRetryableException.class);
        verify(reportService).failBuild(31L, "temporary market failure");
    }

    private InvestmentJobClaim claim(String inputJson, int attempts, int maxAttempts) {
        return new InvestmentJobClaim(88L, 11L, InvestmentJobType.REPORT_BUILD, inputJson,
                attempts, maxAttempts, "worker", "token", NOW, NOW.plusSeconds(60));
    }

    private InvestmentResearchReportPo report(long id, long workspaceId, FrozenResearchReportInput input,
                                                String inputHash, String status) {
        InvestmentResearchReportPo report = new InvestmentResearchReportPo();
        report.setId(id);
        report.setWorkspaceId(workspaceId);
        report.setInstrumentId(input.instrumentId());
        report.setReportType(input.reportType().name());
        report.setSourceType("USER");
        report.setTitle("Pending");
        report.setDataAsOf(input.dataAsOf());
        report.setStatus(status);
        report.setReportVersion(1L);
        try {
            report.setMetricsJson(objectMapper.writeValueAsString(
                    Map.of("input", input, "inputHash", inputHash, "metrics", Map.of())));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        return report;
    }
}
