package top.egon.mario.investment.research.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.access.InvestmentAccessService;
import top.egon.mario.investment.common.job.InvestmentJobEnqueueCommand;
import top.egon.mario.investment.common.job.InvestmentJobEnqueueService;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.research.indicator.ResearchHashSupport;
import top.egon.mario.investment.research.po.InvestmentReportEvidencePo;
import top.egon.mario.investment.research.po.InvestmentResearchReportPo;
import top.egon.mario.investment.research.repository.InvestmentReportEvidenceRepository;
import top.egon.mario.investment.research.repository.InvestmentResearchReportRepository;
import top.egon.mario.investment.research.web.dto.CreateInvestmentReportRequest;
import top.egon.mario.investment.research.web.dto.CreateInvestmentReportResponse;
import top.egon.mario.investment.research.web.dto.InvestmentReportDetailResponse;
import top.egon.mario.investment.research.web.dto.InvestmentReportEvidenceResponse;
import top.egon.mario.investment.research.web.dto.InvestmentReportSummaryResponse;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Creates queued report versions and persists deterministic generator output within owner boundaries.
 */
@Service
@RequiredArgsConstructor
public class InvestmentReportService {

    private static final String PENDING = "PENDING";
    private static final String READY = "READY";
    private static final String FAILED = "FAILED";

    private final InvestmentResearchReportRepository reportRepository;
    private final InvestmentReportEvidenceRepository evidenceRepository;
    private final InvestmentAccessService accessService;
    private final InvestmentJobEnqueueService jobEnqueueService;
    private final InvestmentResearchReportGeneratorRegistry generatorRegistry;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public CreateInvestmentReportResponse create(Long actorId, Long workspaceId,
                                                 CreateInvestmentReportRequest request) {
        accessService.requireWorkspaceOwner(workspaceId, actorId);
        Instant dataAsOf = clock.instant();
        FrozenResearchReportInput input = normalizedInput(request, dataAsOf);
        generatorRegistry.require(input.reportType());
        String inputHash = ResearchHashSupport.sha256(input.canonicalValue());

        InvestmentResearchReportPo report = new InvestmentResearchReportPo();
        report.setWorkspaceId(workspaceId);
        report.setInstrumentId(input.instrumentId());
        report.setReportType(input.reportType().name());
        report.setSourceType("USER");
        report.setTitle(pendingTitle(input.reportType()));
        report.setDataAsOf(dataAsOf);
        report.setStatus(PENDING);
        report.setReportVersion(1L);
        report.setMetricsJson(json(new PersistedResearchMetrics(input, inputHash, null, Map.of())));
        report.setCreatedBy(actorId);
        report.setUpdatedBy(actorId);
        report = reportRepository.saveAndFlush(report);

        InvestmentReportBuildInput buildInput = new InvestmentReportBuildInput(
                report.getId(), report.getReportVersion());
        long jobId = jobEnqueueService.enqueue(new InvestmentJobEnqueueCommand(
                workspaceId, InvestmentJobType.REPORT_BUILD, 100, dataAsOf, 3,
                "report-build:%d:v%d".formatted(report.getId(), report.getReportVersion()), json(buildInput)));
        return new CreateInvestmentReportResponse(toSummary(report), jobId);
    }

    @Transactional(readOnly = true)
    public Page<InvestmentReportSummaryResponse> list(Long actorId, Long workspaceId,
                                                      String requestedType, Pageable pageable) {
        accessService.requireWorkspaceOwner(workspaceId, actorId);
        String reportType = null;
        if (StringUtils.hasText(requestedType)) {
            reportType = requiredReportType(requestedType).name();
        }
        return reportRepository.findOwnedReports(workspaceId, actorId, reportType, pageable)
                .map(InvestmentReportService::toSummary);
    }

    @Transactional(readOnly = true)
    public InvestmentReportDetailResponse detail(Long actorId, Long reportId) {
        InvestmentResearchReportPo report = reportRepository.findOwnedReport(reportId, actorId)
                .orElseThrow(() -> new InvestmentException(InvestmentErrorCode.FORBIDDEN,
                        "Investment report access denied"));
        List<InvestmentReportEvidenceResponse> evidence = evidenceRepository
                .findAllByReportIdOrderByIdAsc(report.getId()).stream().map(this::toEvidence).toList();
        return new InvestmentReportDetailResponse(toSummary(report), report.getSourceType(),
                report.getContentMarkdown(), report.getMetricsJson(), evidence);
    }

    /**
     * Reads and validates the immutable generation context in a short transaction.
     */
    @Transactional(readOnly = true)
    public PreparedResearchReport prepareBuild(long reportId, long expectedVersion) {
        InvestmentResearchReportPo report = requireReport(reportId);
        if (report.getReportVersion() != expectedVersion) {
            throw invalidBuild("Report version does not match the queued job");
        }
        if (FAILED.equals(report.getStatus())) {
            throw invalidBuild("Failed report versions cannot be rebuilt");
        }
        PersistedResearchMetrics persisted = persistedMetrics(report.getMetricsJson());
        validateFrozenInput(report, persisted);
        return new PreparedResearchReport(new ResearchReportGenerationContext(
                report.getId(), report.getWorkspaceId(), report.getReportVersion(),
                persisted.input(), persisted.inputHash()), report.getStatus());
    }

    /**
     * Wins the PENDING-to-READY transition before appending evidence in the same short transaction.
     */
    @Transactional
    public void completeBuild(ResearchReportGenerationContext context, GeneratedResearchReport generated) {
        InvestmentResearchReportPo report = requireReport(context.reportId());
        if (READY.equals(report.getStatus())) {
            return;
        }
        if (!PENDING.equals(report.getStatus())) {
            throw invalidBuild("Only pending report versions can be completed");
        }
        validateContext(report, context);
        validateGenerated(generated);
        report.setTitle(generated.title());
        report.setSummary(generated.summary());
        report.setContentMarkdown(generated.contentMarkdown());
        report.setMetricsJson(json(new PersistedResearchMetrics(
                context.input(), context.inputHash(), generated.indicatorSnapshot(), generated.metrics())));
        report.setStatus(READY);
        reportRepository.saveAndFlush(report);
        evidenceRepository.saveAll(generated.evidence().stream()
                .map(evidence -> toEvidence(report, evidence)).toList());
        evidenceRepository.flush();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failBuild(long reportId, String message) {
        InvestmentResearchReportPo report = requireReport(reportId);
        if (!PENDING.equals(report.getStatus())) {
            return;
        }
        report.setStatus(FAILED);
        report.setSummary(truncate(message, 2_000, "Report generation failed"));
        reportRepository.saveAndFlush(report);
    }

    private FrozenResearchReportInput normalizedInput(CreateInvestmentReportRequest request, Instant dataAsOf) {
        if (request == null) {
            throw invalid("Research report request is required");
        }
        InvestmentReportType reportType = requiredReportType(request.reportType());
        if (reportType == InvestmentReportType.MARKET_OVERVIEW) {
            if (request.instrumentId() != null || StringUtils.hasText(request.priceType())
                    || StringUtils.hasText(request.interval()) || request.fromInclusive() != null
                    || request.toExclusive() != null) {
                throw invalid("MARKET_OVERVIEW does not accept instrument or candle dimensions");
            }
            return new FrozenResearchReportInput(reportType, null, null, null, null, null, dataAsOf);
        }
        if (reportType != InvestmentReportType.INSTRUMENT_ANALYSIS) {
            return new FrozenResearchReportInput(reportType, request.instrumentId(), null, null,
                    request.fromInclusive(), request.toExclusive(), dataAsOf);
        }
        if (request.instrumentId() == null || request.instrumentId() <= 0) {
            throw invalid("INSTRUMENT_ANALYSIS requires a positive instrumentId");
        }
        PriceType priceType = enumValue(PriceType.class, request.priceType(), "priceType");
        BarInterval interval = enumValue(BarInterval.class, request.interval(), "interval");
        if (priceType == PriceType.NONE || interval == BarInterval.NONE) {
            throw invalid("INSTRUMENT_ANALYSIS requires concrete priceType and interval values");
        }
        if (request.fromInclusive() == null || request.toExclusive() == null
                || !request.toExclusive().isAfter(request.fromInclusive())
                || request.toExclusive().isAfter(dataAsOf)) {
            throw invalid("INSTRUMENT_ANALYSIS requires a valid completed time range at the report cutoff");
        }
        return new FrozenResearchReportInput(reportType, request.instrumentId(), priceType, interval,
                request.fromInclusive(), request.toExclusive(), dataAsOf);
    }

    private void validateFrozenInput(InvestmentResearchReportPo report, PersistedResearchMetrics persisted) {
        if (persisted == null || persisted.input() == null || !StringUtils.hasText(persisted.inputHash())) {
            throw invalidBuild("Persisted report input is incomplete");
        }
        FrozenResearchReportInput input = persisted.input();
        String expectedHash = ResearchHashSupport.sha256(input.canonicalValue());
        if (!Objects.equals(expectedHash, persisted.inputHash())
                || !Objects.equals(report.getReportType(), input.reportType().name())
                || !Objects.equals(report.getInstrumentId(), input.instrumentId())
                || !Objects.equals(report.getDataAsOf(), input.dataAsOf())) {
            throw invalidBuild("Persisted report input hash or ownership boundary is invalid");
        }
    }

    private void validateContext(InvestmentResearchReportPo report, ResearchReportGenerationContext context) {
        if (!Objects.equals(report.getWorkspaceId(), context.workspaceId())
                || report.getReportVersion() != context.reportVersion()
                || !Objects.equals(report.getDataAsOf(), context.input().dataAsOf())
                || !Objects.equals(report.getReportType(), context.input().reportType().name())
                || !Objects.equals(report.getInstrumentId(), context.input().instrumentId())
                || !Objects.equals(context.inputHash(), ResearchHashSupport.sha256(context.input().canonicalValue()))) {
            throw invalidBuild("Generated report does not match the persisted version boundary");
        }
    }

    private void validateGenerated(GeneratedResearchReport generated) {
        if (generated == null || !StringUtils.hasText(generated.title()) || generated.title().length() > 256
                || generated.summary() == null || generated.contentMarkdown() == null
                || generated.metrics() == null || generated.evidence() == null || generated.evidence().isEmpty()) {
            throw invalidBuild("Report generator returned an incomplete artifact");
        }
        ResearchMarkdownPolicy.requireSafe(generated.contentMarkdown());
    }

    private InvestmentReportEvidencePo toEvidence(InvestmentResearchReportPo report,
                                                    ResearchReportEvidenceDraft draft) {
        if (draft == null || draft.sourceId() <= 0 || draft.dataStartTime() == null
                || draft.dataEndTime() == null || draft.dataAsOf() == null
                || draft.dataEndTime().isBefore(draft.dataStartTime())
                || !draft.dataAsOf().equals(report.getDataAsOf())
                || draft.dataEndTime().isAfter(report.getDataAsOf())
                || !StringUtils.hasText(draft.evidenceType()) || !StringUtils.hasText(draft.sourceReference())
                || !StringUtils.hasText(draft.payloadHash()) || draft.payloadHash().length() != 64) {
            throw invalidBuild("Report evidence violates the immutable owner or cutoff boundary");
        }
        InvestmentReportEvidencePo evidence = new InvestmentReportEvidencePo();
        evidence.setReportId(report.getId());
        evidence.setEvidenceType(draft.evidenceType());
        evidence.setSourceId(draft.sourceId());
        evidence.setInstrumentId(draft.instrumentId());
        evidence.setDataStartTime(draft.dataStartTime());
        evidence.setDataEndTime(draft.dataEndTime());
        evidence.setDataAsOf(draft.dataAsOf());
        evidence.setSourceReference(draft.sourceReference());
        evidence.setPayloadHash(draft.payloadHash());
        evidence.setMetadataJson(normalizedJson(draft.metadataJson()));
        return evidence;
    }

    private InvestmentResearchReportPo requireReport(long reportId) {
        return reportRepository.findByIdAndDeletedFalse(reportId)
                .orElseThrow(() -> new InvestmentException(InvestmentErrorCode.NOT_FOUND,
                        "Research report does not exist: " + reportId));
    }

    private PersistedResearchMetrics persistedMetrics(String value) {
        try {
            return objectMapper.readValue(value, PersistedResearchMetrics.class);
        } catch (JsonProcessingException exception) {
            throw invalidBuild("Persisted report input JSON is invalid", exception);
        }
    }

    private String normalizedJson(String value) {
        try {
            JsonNode node = objectMapper.readTree(StringUtils.hasText(value) ? value : "{}");
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw invalidBuild("Evidence metadata JSON is invalid", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize research report state", exception);
        }
    }

    private InvestmentReportEvidenceResponse toEvidence(InvestmentReportEvidencePo evidence) {
        return new InvestmentReportEvidenceResponse(
                evidence.getId(), evidence.getEvidenceType(), evidence.getSourceId(), evidence.getInstrumentId(),
                evidence.getDataStartTime(), evidence.getDataEndTime(), evidence.getDataAsOf(),
                evidence.getSourceReference(), evidence.getPayloadHash(), evidence.getMetadataJson(),
                evidence.getCreatedAt());
    }

    private static InvestmentReportSummaryResponse toSummary(InvestmentResearchReportPo report) {
        return new InvestmentReportSummaryResponse(
                report.getId(), report.getWorkspaceId(), report.getInstrumentId(), report.getReportType(),
                report.getTitle(), report.getSummary(), report.getStatus(), report.getReportVersion(),
                report.getDataAsOf(), report.getCreatedAt());
    }

    private static InvestmentReportType requiredReportType(String value) {
        InvestmentReportType reportType = InvestmentReportType.parse(value);
        if (reportType == null) {
            throw invalid("reportType is required");
        }
        return reportType;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw invalid(name + " is required");
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw invalid("Unsupported " + name + ": " + value);
        }
    }

    private static String pendingTitle(InvestmentReportType type) {
        return switch (type) {
            case MARKET_OVERVIEW -> "市场概览（生成中）";
            case INSTRUMENT_ANALYSIS -> "合约分析（生成中）";
            default -> type.name() + "（生成中）";
        };
    }

    private static String truncate(String value, int maxLength, String fallback) {
        String normalized = StringUtils.hasText(value) ? value : fallback;
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private static InvestmentException invalid(String message) {
        return new InvestmentException(InvestmentErrorCode.INVALID_REQUEST, message);
    }

    private static InvestmentException invalidBuild(String message) {
        return new InvestmentException(InvestmentErrorCode.INVALID_REQUEST, message);
    }

    private static InvestmentException invalidBuild(String message, Throwable cause) {
        return new InvestmentException(InvestmentErrorCode.INVALID_REQUEST, message, cause);
    }
}
