package top.egon.mario.investment.quant.backtest;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.investment.common.job.InvestmentJobEnqueueCommand;
import top.egon.mario.investment.common.job.InvestmentJobEnqueueService;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.quant.po.InvestmentBacktestRunPo;
import top.egon.mario.investment.quant.repository.InvestmentBacktestRunRepository;
import top.egon.mario.investment.quant.strategy.StrategyDescriptor;

import java.math.BigDecimal;
import java.time.Clock;

@Service
public class BacktestSubmissionPersistenceService {

    private final InvestmentJobEnqueueService enqueueService;
    private final InvestmentBacktestRunRepository runRepository;
    private final Clock clock;

    public BacktestSubmissionPersistenceService(InvestmentJobEnqueueService enqueueService,
                                                InvestmentBacktestRunRepository runRepository,
                                                Clock clock) {
        this.enqueueService = enqueueService;
        this.runRepository = runRepository;
        this.clock = clock;
    }

    @Transactional
    public InvestmentBacktestRunPo submit(long actorId, long workspaceId, long strategyReleaseId,
                                          long snapshotId, BigDecimal initialEquity,
                                          StrategyDescriptor descriptor, String idempotencyKey) {
        long jobId = enqueueService.enqueue(new InvestmentJobEnqueueCommand(workspaceId,
                InvestmentJobType.BACKTEST_RUN, 100, clock.instant(), 3, idempotencyKey,
                "{\"workspaceId\":" + workspaceId + "}"));
        return runRepository.findByJobIdAndDeletedFalse(jobId).orElseGet(() -> {
            InvestmentBacktestRunPo run = new InvestmentBacktestRunPo();
            run.setWorkspaceId(workspaceId);
            run.setJobId(jobId);
            run.setStrategyReleaseId(strategyReleaseId);
            run.setDatasetSnapshotId(snapshotId);
            run.setStatus("QUEUED");
            run.setInitialEquity(initialEquity);
            run.setBaseCurrency("USDT");
            run.setMarginMode("ISOLATED");
            run.setPositionMode("ONE_WAY");
            run.setFeeModelCode(descriptor.feeModelCode());
            run.setSlippageModelCode(descriptor.slippageModelCode());
            run.setMatchingModelCode(descriptor.matchingModelCode());
            run.setCreatedBy(actorId);
            run.setUpdatedBy(actorId);
            return runRepository.saveAndFlush(run);
        });
    }
}
