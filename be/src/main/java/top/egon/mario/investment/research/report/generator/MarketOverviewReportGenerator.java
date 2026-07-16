package top.egon.mario.investment.research.report.generator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import top.egon.mario.investment.marketdata.query.InvestmentMarketQueryService;
import top.egon.mario.investment.marketdata.web.dto.InvestmentMarketOverviewResponse;
import top.egon.mario.investment.research.indicator.ResearchHashSupport;
import top.egon.mario.investment.research.report.GeneratedResearchReport;
import top.egon.mario.investment.research.report.InvestmentReportType;
import top.egon.mario.investment.research.report.InvestmentResearchReportGenerator;
import top.egon.mario.investment.research.report.ResearchEvidenceSource;
import top.egon.mario.investment.research.report.ResearchEvidenceSourceService;
import top.egon.mario.investment.research.report.ResearchReportEvidenceDraft;
import top.egon.mario.investment.research.report.ResearchReportGenerationContext;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic market-health report backed only by the shared market query facade.
 */
@Component
public class MarketOverviewReportGenerator implements InvestmentResearchReportGenerator {

    private final InvestmentMarketQueryService marketQueryService;
    private final ResearchEvidenceSourceService evidenceSourceService;
    private final ObjectMapper objectMapper;

    public MarketOverviewReportGenerator(InvestmentMarketQueryService marketQueryService,
                                         ResearchEvidenceSourceService evidenceSourceService,
                                         ObjectMapper objectMapper) {
        this.marketQueryService = marketQueryService;
        this.evidenceSourceService = evidenceSourceService;
        this.objectMapper = objectMapper;
    }

    @Override
    public InvestmentReportType reportType() {
        return InvestmentReportType.MARKET_OVERVIEW;
    }

    @Override
    public GeneratedResearchReport generate(ResearchReportGenerationContext context) {
        Instant cutoff = context.input().dataAsOf();
        InvestmentMarketOverviewResponse overview = marketQueryService.overview(cutoff);
        Map<String, Object> metrics = metrics(overview);
        String canonicalMetrics = json(metrics);
        List<ResearchReportEvidenceDraft> evidence = evidenceSourceService.requireMarketSources().stream()
                .map(source -> evidence(source, cutoff, canonicalMetrics)).toList();
        String summary = "代码订阅市场共 %d 个标的，%d 个报价新鲜，%d 个报价过期或缺失。".formatted(
                overview.subscribedInstrumentCount(), overview.freshQuoteCount(),
                overview.staleOrMissingQuoteCount());
        String markdown = """
                # 市场概览

                - 数据截止：%s
                - 代码订阅标的：%d
                - 新鲜报价：%d
                - 过期或缺失报价：%d
                - 未解决数据质量问题：%d

                本报告中的数量均来自冻结截止时间下的市场查询服务。
                """.formatted(cutoff, overview.subscribedInstrumentCount(), overview.freshQuoteCount(),
                overview.staleOrMissingQuoteCount(), overview.openQualityIssueCount());
        return new GeneratedResearchReport(
                "市场概览 · " + cutoff, summary, markdown, metrics, null, evidence);
    }

    private Map<String, Object> metrics(InvestmentMarketOverviewResponse overview) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("subscribedInstrumentCount", overview.subscribedInstrumentCount());
        metrics.put("freshQuoteCount", overview.freshQuoteCount());
        metrics.put("staleOrMissingQuoteCount", overview.staleOrMissingQuoteCount());
        metrics.put("openQualityIssueCount", overview.openQualityIssueCount());
        return metrics;
    }

    private ResearchReportEvidenceDraft evidence(ResearchEvidenceSource source, Instant cutoff,
                                                  String canonicalMetrics) {
        return new ResearchReportEvidenceDraft(
                "MARKET_OVERVIEW", source.sourceId(), source.instrumentId(), cutoff, cutoff, cutoff,
                "source:%d/instrument:%d/market-overview".formatted(source.sourceId(), source.instrumentId()),
                ResearchHashSupport.sha256(canonicalMetrics),
                json(Map.of("sourceCode", source.sourceCode(), "externalSymbol", source.externalSymbol())));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize market report data", exception);
        }
    }
}
