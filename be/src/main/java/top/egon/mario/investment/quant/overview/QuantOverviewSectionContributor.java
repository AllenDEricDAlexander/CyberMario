package top.egon.mario.investment.quant.overview;

import org.springframework.stereotype.Component;
import top.egon.mario.investment.overview.InvestmentOverviewSectionContributor;
import top.egon.mario.investment.overview.dto.InvestmentOverviewSectionResponse;
import top.egon.mario.investment.quant.po.InvestmentBacktestRunPo;
import top.egon.mario.investment.quant.repository.InvestmentBacktestRunRepository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class QuantOverviewSectionContributor implements InvestmentOverviewSectionContributor {

    private final InvestmentBacktestRunRepository repository;

    public QuantOverviewSectionContributor(InvestmentBacktestRunRepository repository) {
        this.repository = repository;
    }

    @Override
    public String sectionCode() {
        return "QUANT";
    }

    @Override
    public int order() {
        return 300;
    }

    @Override
    public InvestmentOverviewSectionResponse contribute(OverviewContext context) {
        List<Map<String, Object>> recent = repository
                .findTop5ByWorkspaceIdAndStatusAndFinishedAtLessThanEqualAndDeletedFalseOrderByFinishedAtDescIdDesc(
                        context.workspaceId(), "SUCCEEDED", context.dataAsOf())
                .stream().map(this::run).toList();
        return new InvestmentOverviewSectionResponse(sectionCode(), "AVAILABLE", context.dataAsOf(),
                Map.of("recentBacktests", recent), null);
    }

    private Map<String, Object> run(InvestmentBacktestRunPo value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", value.getId());
        result.put("strategyReleaseId", value.getStrategyReleaseId());
        result.put("datasetSnapshotId", value.getDatasetSnapshotId());
        result.put("totalReturn", text(value.getTotalReturn()));
        result.put("maxDrawdown", text(value.getMaxDrawdown()));
        result.put("winRate", text(value.getWinRate()));
        result.put("tradeCount", value.getTradeCount());
        result.put("finishedAt", value.getFinishedAt());
        return result;
    }

    private static String text(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
