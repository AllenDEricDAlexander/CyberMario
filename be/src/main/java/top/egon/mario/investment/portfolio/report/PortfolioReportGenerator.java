package top.egon.mario.investment.portfolio.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import top.egon.mario.investment.portfolio.query.InvestmentPortfolioQueryService;
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

/** Immutable workspace portfolio report generated from one cutoff projection. */
@Component
public class PortfolioReportGenerator implements InvestmentResearchReportGenerator {

    private final InvestmentPortfolioQueryService queryService;
    private final ResearchEvidenceSourceService evidenceSourceService;
    private final ObjectMapper objectMapper;

    public PortfolioReportGenerator(
            InvestmentPortfolioQueryService queryService,
            ResearchEvidenceSourceService evidenceSourceService,
            ObjectMapper objectMapper) {
        this.queryService = queryService;
        this.evidenceSourceService = evidenceSourceService;
        this.objectMapper = objectMapper;
    }

    @Override
    public InvestmentReportType reportType() {
        return InvestmentReportType.PORTFOLIO_REPORT;
    }

    @Override
    public GeneratedResearchReport generate(ResearchReportGenerationContext context) {
        Instant cutoff = context.input().dataAsOf();
        var summary = queryService.workspaceSummary(context.workspaceId(), cutoff);
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("accountCount", summary.accountCount());
        metrics.put("positionCount", summary.positionCount());
        metrics.put("walletBalance", text(summary.walletBalance()));
        metrics.put("equity", text(summary.equity()));
        metrics.put("availableBalance", text(summary.availableBalance()));
        metrics.put("unrealizedPnl", text(summary.unrealizedPnl()));
        metrics.put("grossExposure", text(summary.grossExposure()));
        metrics.put("maxDrawdown", text(summary.maxDrawdown()));
        metrics.put("riskWarningCount", summary.riskWarningCount());
        metrics.put("positions", queryService.workspacePositions(context.workspaceId(), cutoff));
        String canonical = json(metrics);
        List<ResearchReportEvidenceDraft> evidence = evidenceSourceService.requireMarketSources().stream()
                .map(source -> evidence(source, cutoff, canonical)).toList();
        String summaryText = "模拟组合共 %d 个账户、%d 个持仓，权益 %s USDT，风险提醒 %d 条。".formatted(
                summary.accountCount(), summary.positionCount(), text(summary.equity()),
                summary.riskWarningCount());
        String markdown = """
                # 模拟组合报告

                - 数据截止：%s
                - 模拟账户：%d
                - 当前持仓：%d
                - 钱包余额：%s USDT
                - 账户权益：%s USDT
                - 未实现损益：%s USDT
                - 总敞口：%s USDT
                - 最大回撤：%s
                - 风险提醒：%d

                本报告仅描述模拟盘事实，不代表实盘收益。
                """.formatted(cutoff, summary.accountCount(), summary.positionCount(),
                text(summary.walletBalance()), text(summary.equity()), text(summary.unrealizedPnl()),
                text(summary.grossExposure()), text(summary.maxDrawdown()), summary.riskWarningCount());
        return new GeneratedResearchReport(
                "模拟组合报告 · " + cutoff, summaryText, markdown, metrics, null, evidence);
    }

    private ResearchReportEvidenceDraft evidence(
            ResearchEvidenceSource source, Instant cutoff, String canonical) {
        return new ResearchReportEvidenceDraft(
                "PORTFOLIO", source.sourceId(), source.instrumentId(), cutoff, cutoff, cutoff,
                "source:%d/instrument:%d/portfolio".formatted(source.sourceId(), source.instrumentId()),
                ResearchHashSupport.sha256(canonical),
                json(Map.of("sourceCode", source.sourceCode(), "externalSymbol", source.externalSymbol())));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize portfolio report data", exception);
        }
    }

    private static String text(java.math.BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
