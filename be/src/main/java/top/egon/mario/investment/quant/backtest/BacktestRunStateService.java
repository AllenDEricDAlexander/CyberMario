package top.egon.mario.investment.quant.backtest;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.investment.quant.backtest.model.BacktestMetrics;
import top.egon.mario.investment.quant.po.InvestmentBacktestRunPo;
import top.egon.mario.investment.quant.repository.InvestmentBacktestRunRepository;

import java.time.Clock;

@Service
public class BacktestRunStateService {

    private final InvestmentBacktestRunRepository repository;
    private final Clock clock;

    public BacktestRunStateService(InvestmentBacktestRunRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public InvestmentBacktestRunPo markRunning(long jobId) {
        InvestmentBacktestRunPo run = repository.findByJobIdAndDeletedFalse(jobId)
                .orElseThrow(() -> new IllegalStateException("Backtest run not found for job"));
        if ("SUCCEEDED".equals(run.getStatus())) {
            return run;
        }
        run.setStatus("RUNNING");
        if (run.getStartedAt() == null) {
            run.setStartedAt(clock.instant());
        }
        run.setErrorCode(null);
        run.setErrorMessage(null);
        return repository.saveAndFlush(run);
    }

    @Transactional
    public void markSucceeded(long runId, BacktestMetrics metrics) {
        InvestmentBacktestRunPo run = repository.findById(runId).orElseThrow();
        run.setTotalReturn(metrics.totalReturn());
        run.setAnnualizedReturn(metrics.annualizedReturn());
        run.setMaxDrawdown(metrics.maxDrawdown());
        run.setSharpeRatio(metrics.sharpeRatio());
        run.setSortinoRatio(metrics.sortinoRatio());
        run.setWinRate(metrics.winRate());
        run.setProfitFactor(metrics.profitFactor());
        run.setTurnover(metrics.turnover());
        run.setTradeCount(metrics.tradeCount());
        run.setTotalFee(metrics.totalFee());
        run.setTotalFunding(metrics.totalFunding());
        run.setLiquidationCount(metrics.liquidationCount());
        run.setStatus("SUCCEEDED");
        run.setFinishedAt(clock.instant());
        repository.saveAndFlush(run);
    }

    @Transactional
    public void markFailed(long runId, String code, String message) {
        InvestmentBacktestRunPo run = repository.findById(runId).orElseThrow();
        run.setStatus("FAILED");
        run.setErrorCode(code);
        run.setErrorMessage(message == null ? "Backtest failed" : message.substring(0, Math.min(2000, message.length())));
        run.setFinishedAt(clock.instant());
        repository.saveAndFlush(run);
    }
}
