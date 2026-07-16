package top.egon.mario.investment.trading.matching;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import top.egon.mario.investment.common.job.InvestmentJobClaim;
import top.egon.mario.investment.common.job.InvestmentJobHandler;
import top.egon.mario.investment.common.job.InvestmentJobHandlerResult;
import top.egon.mario.investment.common.job.InvestmentJobNonRetryableException;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.trading.matching.model.ContractTerms;
import top.egon.mario.investment.trading.matching.model.MatchResult;
import top.egon.mario.investment.trading.matching.model.MatchStatus;
import top.egon.mario.investment.trading.matching.model.MatchingOrder;
import top.egon.mario.investment.trading.service.PaperExecutionTransactionService;
import top.egon.mario.investment.trading.service.PaperOrderService;
import top.egon.mario.investment.trading.service.model.PaperExecutionResult;

import java.time.Clock;
import java.time.Instant;

/**
 * Replays closed bars outside a transaction and delegates one idempotent financial transaction on fill.
 */
@Component
public class PaperMatchJobHandler implements InvestmentJobHandler {

    private final PaperOrderService orderService;
    private final PaperMatchingMarketReader marketReader;
    private final PaperExecutionTransactionService executionService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PaperMatchJobHandler(
            PaperOrderService orderService,
            PaperMatchingMarketReader marketReader,
            PaperExecutionTransactionService executionService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.orderService = orderService;
        this.marketReader = marketReader;
        this.executionService = executionService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public InvestmentJobType jobType() {
        return InvestmentJobType.PAPER_MATCH;
    }

    @Override
    public InvestmentJobHandlerResult execute(InvestmentJobClaim claim) {
        PaperMatchJobInput input = input(claim.inputJson());
        PaperOrderService.MatchCandidate candidate = orderService.findMatchCandidate(input.orderId()).orElse(null);
        if (candidate == null || !"PENDING_MATCH".equals(candidate.status())) {
            return InvestmentJobHandlerResult.completed("{\"status\":\"NOOP\"}");
        }
        Instant now = clock.instant();
        if (candidate.expiresAt() != null && !candidate.expiresAt().isAfter(now)) {
            String status = executionService.expire(input, now);
            return InvestmentJobHandlerResult.completed("{\"status\":\"" + status + "\"}");
        }
        MatchingOrder matchingOrder = new MatchingOrder(
                candidate.orderId(), candidate.orderType(), candidate.positionSide(), candidate.positionAction(),
                candidate.quantity(), candidate.limitPrice(), input.eligibleAfter());
        BarMatchingModel model = new BarMatchingModel(
                new FixedBpsSlippageModel(input.slippageBps()),
                new RateFeeModel(input.makerFeeRate(), input.takerFeeRate()));
        ContractTerms terms = new ContractTerms(
                input.priceStep(), input.quantityStep(), input.contractMultiplier());
        for (var bar : marketReader.closedBars(
                input.sourceId(), input.instrumentId(), input.eligibleAfter(), now)) {
            MatchResult match = model.match(matchingOrder, bar, terms);
            if (match.status() == MatchStatus.FILLED) {
                PaperExecutionResult result = executionService.execute(input, match, now);
                return InvestmentJobHandlerResult.completed(json(result));
            }
        }
        return InvestmentJobHandlerResult.deferred(now.plusSeconds(60));
    }

    private PaperMatchJobInput input(String json) {
        try {
            return objectMapper.readValue(json, PaperMatchJobInput.class);
        } catch (JsonProcessingException exception) {
            throw new InvestmentJobNonRetryableException(
                    "PAPER_MATCH_INPUT_INVALID", "Paper matching input is invalid", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new InvestmentJobNonRetryableException(
                    "PAPER_MATCH_RESULT_INVALID", "Paper matching result is invalid", exception);
        }
    }
}
