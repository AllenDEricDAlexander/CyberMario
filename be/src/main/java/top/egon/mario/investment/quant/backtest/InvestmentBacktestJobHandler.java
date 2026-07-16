package top.egon.mario.investment.quant.backtest;

import org.springframework.stereotype.Component;
import top.egon.mario.investment.common.job.InvestmentJobClaim;
import top.egon.mario.investment.common.job.InvestmentJobHandler;
import top.egon.mario.investment.common.job.InvestmentJobHandlerResult;
import top.egon.mario.investment.common.job.InvestmentJobNonRetryableException;
import top.egon.mario.investment.common.job.InvestmentJobRetryableException;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.quant.backtest.model.BacktestResult;
import top.egon.mario.investment.quant.engine.BacktestEngine;
import top.egon.mario.investment.quant.po.InvestmentBacktestRunPo;
import top.egon.mario.investment.quant.po.InvestmentStrategyReleasePo;
import top.egon.mario.investment.quant.repository.InvestmentStrategyReleaseRepository;
import top.egon.mario.investment.quant.strategy.InvestmentStrategy;
import top.egon.mario.investment.quant.strategy.InvestmentStrategyRegistry;

/**
 * Durable job adapter; engine computation runs under the runtime's transaction-free handler boundary.
 */
@Component
public class InvestmentBacktestJobHandler implements InvestmentJobHandler {

    private final BacktestRunStateService stateService;
    private final InvestmentStrategyReleaseRepository strategyReleaseRepository;
    private final InvestmentStrategyRegistry strategyRegistry;
    private final BacktestDatasetLoader datasetLoader;
    private final BacktestEngine engine;
    private final BacktestResultPersistenceService resultPersistenceService;

    public InvestmentBacktestJobHandler(BacktestRunStateService stateService,
                                        InvestmentStrategyReleaseRepository strategyReleaseRepository,
                                        InvestmentStrategyRegistry strategyRegistry,
                                        BacktestDatasetLoader datasetLoader,
                                        BacktestEngine engine,
                                        BacktestResultPersistenceService resultPersistenceService) {
        this.stateService = stateService;
        this.strategyReleaseRepository = strategyReleaseRepository;
        this.strategyRegistry = strategyRegistry;
        this.datasetLoader = datasetLoader;
        this.engine = engine;
        this.resultPersistenceService = resultPersistenceService;
    }

    @Override
    public InvestmentJobType jobType() {
        return InvestmentJobType.BACKTEST_RUN;
    }

    @Override
    public InvestmentJobHandlerResult execute(InvestmentJobClaim claim) {
        InvestmentBacktestRunPo run = stateService.markRunning(claim.id());
        if ("SUCCEEDED".equals(run.getStatus())) {
            return InvestmentJobHandlerResult.completed("{\"runId\":" + run.getId() + "}");
        }
        try {
            InvestmentStrategyReleasePo release = strategyReleaseRepository.findById(run.getStrategyReleaseId())
                    .orElseThrow(() -> new IllegalArgumentException("Strategy release not found"));
            InvestmentStrategy strategy = strategyRegistry.require(
                    release.getStrategyCode(), release.getStrategyVersion());
            BacktestResult result = engine.run(datasetLoader.load(
                    run.getDatasetSnapshotId(), strategy, run.getInitialEquity()));
            resultPersistenceService.persist(run.getId(), result);
            stateService.markSucceeded(run.getId(), result.metrics());
            return InvestmentJobHandlerResult.completed("{\"runId\":" + run.getId() + "}");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            stateService.markFailed(run.getId(), "BACKTEST_INPUT_INVALID", exception.getMessage());
            throw new InvestmentJobNonRetryableException(
                    "BACKTEST_INPUT_INVALID", exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            if (claim.attempts() + 1 >= claim.maxAttempts()) {
                stateService.markFailed(run.getId(), "BACKTEST_EXECUTION_FAILED", exception.getMessage());
                throw new InvestmentJobNonRetryableException(
                        "BACKTEST_EXECUTION_FAILED", exception.getMessage(), exception);
            }
            throw new InvestmentJobRetryableException(
                    "BACKTEST_EXECUTION_RETRY", exception.getMessage(), exception);
        }
    }
}
