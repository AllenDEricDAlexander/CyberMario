package top.egon.mario.investment.trading.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import top.egon.mario.investment.common.job.InvestmentJobClaim;
import top.egon.mario.investment.common.job.InvestmentJobHandler;
import top.egon.mario.investment.common.job.InvestmentJobHandlerResult;
import top.egon.mario.investment.common.job.InvestmentJobNonRetryableException;
import top.egon.mario.investment.common.job.InvestmentJobRetryableException;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.trading.service.InvestmentAccountSnapshotService;
import top.egon.mario.investment.trading.service.PaperFundingSettlementService;
import top.egon.mario.investment.trading.service.PaperMaintenanceMarketReader;
import top.egon.mario.investment.trading.service.model.PaperFundingJobInput;

import java.time.Clock;

/** Executes funding I/O outside the short financial and snapshot transactions. */
@Component
public class PaperFundingSettleJobHandler implements InvestmentJobHandler {

    private final PaperMaintenanceMarketReader marketReader;
    private final PaperFundingSettlementService settlementService;
    private final InvestmentAccountSnapshotService snapshotService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PaperFundingSettleJobHandler(
            PaperMaintenanceMarketReader marketReader,
            PaperFundingSettlementService settlementService,
            InvestmentAccountSnapshotService snapshotService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.marketReader = marketReader;
        this.settlementService = settlementService;
        this.snapshotService = snapshotService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public InvestmentJobType jobType() {
        return InvestmentJobType.PAPER_FUNDING_SETTLE;
    }

    @Override
    public InvestmentJobHandlerResult execute(InvestmentJobClaim claim) {
        PaperFundingJobInput input = input(claim.inputJson());
        try {
            var result = settlementService.settle(input, marketReader.funding(input));
            var snapshotTime = clock.instant();
            snapshotService.capture(input.workspaceId(), input.accountId(), snapshotTime,
                    marketReader.accountMarks(input.accountId(), snapshotTime));
            return InvestmentJobHandlerResult.completed(json(result));
        } catch (InvestmentJobNonRetryableException | InvestmentJobRetryableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new InvestmentJobRetryableException(
                    "PAPER_FUNDING_DEPENDENCY_UNAVAILABLE", message(exception), exception);
        }
    }

    private PaperFundingJobInput input(String json) {
        try {
            return objectMapper.readValue(json, PaperFundingJobInput.class);
        } catch (JsonProcessingException exception) {
            throw new InvestmentJobNonRetryableException(
                    "PAPER_FUNDING_INPUT_INVALID", "Paper funding input is invalid", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new InvestmentJobNonRetryableException(
                    "PAPER_FUNDING_RESULT_INVALID", "Paper funding result is invalid", exception);
        }
    }

    private static String message(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
