package top.egon.mario.investment.common.job;

import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Non-transactional command orchestrator for one worker polling cycle.
 */
@Service
public class InvestmentJobWorker {

    private final InvestmentJobClaimService claimService;
    private final InvestmentJobExecutionService executionService;

    public InvestmentJobWorker(InvestmentJobClaimService claimService,
                               InvestmentJobExecutionService executionService) {
        this.claimService = claimService;
        this.executionService = executionService;
    }

    public int processBatch(String workerId, int limit) {
        Assert.isTrue(StringUtils.hasText(workerId), "workerId must not be blank");
        if (limit <= 0) {
            return 0;
        }
        int processed = 0;
        while (processed < limit) {
            Optional<InvestmentJobClaim> claim = claimService.claimNext(workerId);
            if (claim.isEmpty()) {
                break;
            }
            executionService.execute(claim.get());
            processed++;
        }
        return processed;
    }
}
