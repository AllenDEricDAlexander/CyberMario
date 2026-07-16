package top.egon.mario.investment.quant.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.quant.po.InvestmentBacktestRunPo;
import top.egon.mario.investment.quant.po.InvestmentDatasetSnapshotPo;
import top.egon.mario.investment.quant.po.InvestmentStrategyReleasePo;
import top.egon.mario.investment.quant.repository.InvestmentBacktestRunRepository;
import top.egon.mario.investment.quant.repository.InvestmentDatasetSnapshotRepository;
import top.egon.mario.investment.quant.repository.InvestmentStrategyReleaseRepository;

import java.time.Instant;
import java.util.Map;

@Component
class BacktestReportSupport {

    private final InvestmentBacktestRunRepository runRepository;
    private final InvestmentDatasetSnapshotRepository snapshotRepository;
    private final InvestmentStrategyReleaseRepository strategyRepository;
    private final ObjectMapper objectMapper;

    BacktestReportSupport(InvestmentBacktestRunRepository runRepository,
                          InvestmentDatasetSnapshotRepository snapshotRepository,
                          InvestmentStrategyReleaseRepository strategyRepository,
                          ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.snapshotRepository = snapshotRepository;
        this.strategyRepository = strategyRepository;
        this.objectMapper = objectMapper;
    }

    ReportFacts latest(long workspaceId, Instant cutoff) {
        InvestmentBacktestRunPo run = runRepository
                .findFirstByWorkspaceIdAndStatusAndFinishedAtLessThanEqualAndDeletedFalseOrderByFinishedAtDescIdDesc(
                        workspaceId, "SUCCEEDED", cutoff)
                .orElseThrow(() -> new InvestmentException(InvestmentErrorCode.DATA_UNAVAILABLE,
                        "No completed backtest exists at the report cutoff"));
        InvestmentDatasetSnapshotPo snapshot = snapshotRepository.findById(run.getDatasetSnapshotId())
                .orElseThrow(() -> new IllegalStateException("Backtest dataset snapshot not found"));
        InvestmentStrategyReleasePo strategy = strategyRepository.findById(run.getStrategyReleaseId())
                .orElseThrow(() -> new IllegalStateException("Backtest strategy release not found"));
        return new ReportFacts(run, snapshot, strategy, descriptor(strategy.getDescriptorSnapshotJson()));
    }

    private Map<String, Object> descriptor(String json) {
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Strategy descriptor snapshot is invalid", exception);
        }
    }

    record ReportFacts(InvestmentBacktestRunPo run, InvestmentDatasetSnapshotPo snapshot,
                       InvestmentStrategyReleasePo strategy, Map<String, Object> descriptor) {
    }
}
