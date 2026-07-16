package top.egon.mario.investment.quant.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import top.egon.mario.investment.research.report.GeneratedResearchReport;
import top.egon.mario.investment.research.report.InvestmentReportType;
import top.egon.mario.investment.research.report.InvestmentResearchReportGenerator;
import top.egon.mario.investment.research.report.ResearchReportEvidenceDraft;
import top.egon.mario.investment.research.report.ResearchReportGenerationContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class StrategyAnalysisReportGenerator implements InvestmentResearchReportGenerator {

    private final BacktestReportSupport support;
    private final ObjectMapper objectMapper;

    public StrategyAnalysisReportGenerator(BacktestReportSupport support, ObjectMapper objectMapper) {
        this.support = support;
        this.objectMapper = objectMapper;
    }

    @Override
    public InvestmentReportType reportType() {
        return InvestmentReportType.STRATEGY_ANALYSIS;
    }

    @Override
    public GeneratedResearchReport generate(ResearchReportGenerationContext context) {
        BacktestReportSupport.ReportFacts facts = support.latest(
                context.workspaceId(), context.input().dataAsOf());
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("strategyCode", facts.strategy().getStrategyCode());
        metrics.put("strategyVersion", facts.strategy().getStrategyVersion());
        metrics.put("descriptor", facts.descriptor());
        metrics.put("backtestRunId", facts.run().getId());
        metrics.put("totalReturn", text(facts.run().getTotalReturn()));
        metrics.put("maxDrawdown", text(facts.run().getMaxDrawdown()));
        String markdown = """
                # 策略分析：%s / %s

                - 数据截止：%s
                - 代码构建版本：%s
                - 数据集哈希：%s
                - 默认杠杆：%s
                - 总收益率：%s
                - 最大回撤：%s

                策略规则和参数来自已部署 Java 代码；本报告不接受前端策略参数。
                """.formatted(facts.strategy().getStrategyCode(), facts.strategy().getStrategyVersion(),
                context.input().dataAsOf(), facts.strategy().getBuildRevision(), facts.snapshot().getDatasetHash(),
                facts.descriptor().get("defaultLeverage"), text(facts.run().getTotalReturn()),
                text(facts.run().getMaxDrawdown()));
        return new GeneratedResearchReport("策略分析：" + facts.strategy().getDisplayName(),
                "基于固定代码策略和可复现数据集的确定性分析。", markdown, metrics,
                facts.descriptor(), List.of(evidence(context, facts)));
    }

    private ResearchReportEvidenceDraft evidence(ResearchReportGenerationContext context,
                                                  BacktestReportSupport.ReportFacts facts) {
        return new ResearchReportEvidenceDraft("STRATEGY_RELEASE_AND_DATASET", facts.snapshot().getSourceId(), null,
                facts.snapshot().getStartTime(), facts.snapshot().getEndTime(), context.input().dataAsOf(),
                "strategy:%s/%s;dataset:%d".formatted(facts.strategy().getStrategyCode(),
                        facts.strategy().getStrategyVersion(), facts.snapshot().getId()),
                facts.snapshot().getDatasetHash(), json(Map.of("runId", facts.run().getId(),
                        "sourceHash", facts.strategy().getSourceHash(),
                        "datasetHash", facts.snapshot().getDatasetHash())));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String text(java.math.BigDecimal value) {
        return value == null ? "不可用" : value.toPlainString();
    }
}
