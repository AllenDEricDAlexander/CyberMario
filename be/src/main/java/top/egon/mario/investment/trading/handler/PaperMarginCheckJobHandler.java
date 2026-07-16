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
import top.egon.mario.investment.portfolio.repository.InvestmentPositionRepository;
import top.egon.mario.investment.trading.service.InvestmentAccountSnapshotService;
import top.egon.mario.investment.trading.service.PaperMaintenanceMarketReader;
import top.egon.mario.investment.trading.service.PaperMarginCheckService;
import top.egon.mario.investment.trading.service.model.PaperMarginCheckJobInput;
import top.egon.mario.investment.trading.service.model.PaperMarginCheckResult;

import java.math.BigDecimal;
import java.time.Clock;

/** Reads marks outside a transaction and delegates one isolated maintenance check. */
@Component
public class PaperMarginCheckJobHandler implements InvestmentJobHandler {

    private final InvestmentPositionRepository positionRepository;
    private final PaperMaintenanceMarketReader marketReader;
    private final PaperMarginCheckService marginCheckService;
    private final InvestmentAccountSnapshotService snapshotService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PaperMarginCheckJobHandler(
            InvestmentPositionRepository positionRepository,
            PaperMaintenanceMarketReader marketReader,
            PaperMarginCheckService marginCheckService,
            InvestmentAccountSnapshotService snapshotService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.positionRepository = positionRepository;
        this.marketReader = marketReader;
        this.marginCheckService = marginCheckService;
        this.snapshotService = snapshotService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public InvestmentJobType jobType() {
        return InvestmentJobType.PAPER_MARGIN_CHECK;
    }

    @Override
    public InvestmentJobHandlerResult execute(InvestmentJobClaim claim) {
        PaperMarginCheckJobInput input = input(claim.inputJson());
        try {
            BigDecimal quantity = positionRepository
                    .findByIdAndAccountIdAndInstrumentId(
                            input.positionId(), input.accountId(), input.instrumentId())
                    .map(value -> value.getQuantity()).orElse(null);
            PaperMarginCheckResult result;
            if (quantity == null) {
                result = new PaperMarginCheckResult(
                        input.accountId(), input.positionId(), "POSITION_CLOSED",
                        BigDecimal.ZERO, BigDecimal.ZERO, null);
            } else {
                result = marginCheckService.check(input, marketReader.margin(input, quantity), input.dataAsOf());
            }
            var snapshotTime = clock.instant();
            snapshotService.capture(input.workspaceId(), input.accountId(), snapshotTime,
                    marketReader.accountMarks(input.accountId(), snapshotTime));
            return InvestmentJobHandlerResult.completed(json(result));
        } catch (InvestmentJobNonRetryableException | InvestmentJobRetryableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new InvestmentJobRetryableException(
                    "PAPER_MARGIN_DEPENDENCY_UNAVAILABLE", message(exception), exception);
        }
    }

    private PaperMarginCheckJobInput input(String json) {
        try {
            return objectMapper.readValue(json, PaperMarginCheckJobInput.class);
        } catch (JsonProcessingException exception) {
            throw new InvestmentJobNonRetryableException(
                    "PAPER_MARGIN_INPUT_INVALID", "Paper margin input is invalid", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new InvestmentJobNonRetryableException(
                    "PAPER_MARGIN_RESULT_INVALID", "Paper margin result is invalid", exception);
        }
    }

    private static String message(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
