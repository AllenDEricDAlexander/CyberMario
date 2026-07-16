package top.egon.mario.investment.quant.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import top.egon.mario.investment.quant.po.InvestmentBacktestRunPo;
import top.egon.mario.investment.research.report.GeneratedResearchReport;
import top.egon.mario.investment.research.report.InvestmentReportType;
import top.egon.mario.investment.research.report.InvestmentResearchReportGenerator;
import top.egon.mario.investment.research.report.ResearchReportEvidenceDraft;
import top.egon.mario.investment.research.report.ResearchReportGenerationContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class BacktestReportGenerator implements InvestmentResearchReportGenerator {

    private final BacktestReportSupport support;
    private final ObjectMapper objectMapper;

    public BacktestReportGenerator(BacktestReportSupport support, ObjectMapper objectMapper) {
        this.support = support;
        this.objectMapper = objectMapper;
    }

    @Override
    public InvestmentReportType reportType() {
        return InvestmentReportType.BACKTEST_REPORT;
    }

    @Override
    public GeneratedResearchReport generate(ResearchReportGenerationContext context) {
        BacktestReportSupport.ReportFacts facts = support.latest(
                context.workspaceId(), context.input().dataAsOf());
        InvestmentBacktestRunPo run = facts.run();
        Map<String, Object> metrics = metrics(run);
        String markdown = """
                # 回测报告 #%d

                - 数据截止：%s
                - 数据范围：%s 至 %s
                - 数据集哈希：%s
                - 初始权益：%s USDT
                - 总收益率：%s
                - 最大回撤：%s
                - 胜率：%s
                - 成交笔数：%s
                - 手续费：%s
                - 资金费：%s
                - 强平次数：%s

                结果使用已关闭 Bar 的 N/N+1 模拟撮合，不代表真实订单簿成交。
                """.formatted(run.getId(), context.input().dataAsOf(), facts.snapshot().getStartTime(),
                facts.snapshot().getEndTime(), facts.snapshot().getDatasetHash(), text(run.getInitialEquity()),
                text(run.getTotalReturn()), text(run.getMaxDrawdown()), text(run.getWinRate()),
                run.getTradeCount(), text(run.getTotalFee()), text(run.getTotalFunding()), run.getLiquidationCount());
        ResearchReportEvidenceDraft evidence = new ResearchReportEvidenceDraft(
                "BACKTEST_RESULT", facts.snapshot().getSourceId(), null,
                facts.snapshot().getStartTime(), facts.snapshot().getEndTime(), context.input().dataAsOf(),
                "backtest:%d;dataset:%d".formatted(run.getId(), facts.snapshot().getId()),
                facts.snapshot().getDatasetHash(), json(Map.of("runId", run.getId(),
                        "strategyReleaseId", run.getStrategyReleaseId(),
                        "datasetHash", facts.snapshot().getDatasetHash())));
        return new GeneratedResearchReport("回测报告 #" + run.getId(), "确定性 Bar 模拟回测结果。",
                markdown, metrics, null, List.of(evidence));
    }

    private Map<String, Object> metrics(InvestmentBacktestRunPo run) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", run.getId());
        result.put("totalReturn", text(run.getTotalReturn()));
        result.put("annualizedReturn", text(run.getAnnualizedReturn()));
        result.put("maxDrawdown", text(run.getMaxDrawdown()));
        result.put("sharpeRatio", text(run.getSharpeRatio()));
        result.put("sortinoRatio", text(run.getSortinoRatio()));
        result.put("winRate", text(run.getWinRate()));
        result.put("profitFactor", text(run.getProfitFactor()));
        result.put("turnover", text(run.getTurnover()));
        result.put("tradeCount", run.getTradeCount());
        result.put("totalFee", text(run.getTotalFee()));
        result.put("totalFunding", text(run.getTotalFunding()));
        result.put("liquidationCount", run.getLiquidationCount());
        return result;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String text(java.math.BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
