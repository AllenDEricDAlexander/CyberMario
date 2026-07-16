package top.egon.mario.investment.agent.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.investment.agent.model.InvestmentAgentAction;
import top.egon.mario.investment.agent.model.InvestmentAgentExecutionStatus;
import top.egon.mario.investment.agent.model.InvestmentAgentRunInput;
import top.egon.mario.investment.agent.model.InvestmentAgentRunType;
import top.egon.mario.investment.agent.po.InvestmentAgentDecisionPo;
import top.egon.mario.investment.agent.repository.InvestmentAgentDecisionRepository;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.model.PositionAction;
import top.egon.mario.investment.common.model.PositionSide;
import top.egon.mario.investment.portfolio.po.InvestmentPositionPo;
import top.egon.mario.investment.portfolio.repository.InvestmentPositionRepository;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskSource;
import top.egon.mario.investment.trading.service.PaperTradingFacade;
import top.egon.mario.investment.trading.service.model.PaperTradeCommand;
import top.egon.mario.investment.trading.service.model.PaperTradeResult;

import java.time.Clock;
import java.util.List;

/** The only Investment Agent component allowed to enter the paper-trading write facade. */
@Service
public class InvestmentAgentDecisionExecutionService {

    private final InvestmentAgentRunService runService;
    private final InvestmentAgentDecisionRepository decisionRepository;
    private final InvestmentPositionRepository positionRepository;
    private final PaperTradingFacade paperTradingFacade;
    private final Clock clock;

    public InvestmentAgentDecisionExecutionService(
            InvestmentAgentRunService runService,
            InvestmentAgentDecisionRepository decisionRepository,
            InvestmentPositionRepository positionRepository,
            PaperTradingFacade paperTradingFacade,
            Clock clock) {
        this.runService = runService;
        this.decisionRepository = decisionRepository;
        this.positionRepository = positionRepository;
        this.paperTradingFacade = paperTradingFacade;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.NEVER)
    public ExecutionOutcome execute(long decisionId) {
        InvestmentAgentDecisionPo decision = runService.requireDecision(decisionId);
        if (decision.getExecutionStatus() == InvestmentAgentExecutionStatus.NOT_APPLICABLE
                || decision.getExecutionStatus() == InvestmentAgentExecutionStatus.FAILED) {
            return new ExecutionOutcome(decision.getIntentId(), decision.getExecutionStatus(), null);
        }
        if (decision.getExecutionStatus() == InvestmentAgentExecutionStatus.SUBMITTED) {
            return new ExecutionOutcome(decision.getIntentId(), decision.getExecutionStatus(), null);
        }
        InvestmentAgentRunInput input = runService.input(decision.getRunId());
        if (input.runType() != InvestmentAgentRunType.AUTO_TRADE || input.accountId() == null
                || decision.getAction() == InvestmentAgentAction.HOLD) {
            throw new InvestmentException(InvestmentErrorCode.FORBIDDEN,
                    "Only a validated AUTO_TRADE decision can enter paper trading");
        }
        if (!input.instrumentIds().contains(decision.getInstrumentId())
                || !input.dataAsOf().equals(decision.getDataAsOf())) {
            throw new InvestmentException(InvestmentErrorCode.FORBIDDEN,
                    "Investment Agent decision no longer matches its server-bound execution scope");
        }
        if (decision.getExpiresAt() != null && !decision.getExpiresAt().isAfter(clock.instant())) {
            runService.markDecisionFailed(decisionId);
            throw new InvestmentException(InvestmentErrorCode.INVALID_REQUEST,
                    "Investment Agent decision expired before execution");
        }

        TradeShape shape = tradeShape(input.accountId(), decision);
        PaperTradeResult result = paperTradingFacade.submitIntent(new PaperTradeCommand(
                input.actorId(), input.accountId(), decision.getInstrumentId(), InvestmentRiskSource.AGENT,
                "agent:%d:decision:%d".formatted(decision.getRunId(), decision.getId()),
                decision.getExecutionIdempotencyKey(), shape.positionAction(), shape.positionSide(),
                decision.getOrderType(), decision.getRequestedQuantity(), decision.getRequestedNotional(),
                decision.getRequestedLeverage(), decision.getLimitPrice(), shape.reduceOnly(),
                truncate(decision.getThesis(), 2_000), decision.getDataAsOf(), decision.getExpiresAt(), null));
        InvestmentAgentDecisionPo linked = runService.linkIntent(decisionId, result.intentId());
        return new ExecutionOutcome(linked.getIntentId(), linked.getExecutionStatus(), result);
    }

    @Transactional(propagation = Propagation.NEVER)
    public List<ExecutionOutcome> recoverPending() {
        return decisionRepository.findByExecutionStatusAndIntentIdIsNullOrderByIdAsc(
                        InvestmentAgentExecutionStatus.PENDING)
                .stream().map(decision -> execute(decision.getId())).toList();
    }

    private TradeShape tradeShape(long accountId, InvestmentAgentDecisionPo decision) {
        return switch (decision.getAction()) {
            case OPEN_LONG -> new TradeShape(PositionAction.OPEN, PositionSide.LONG, false);
            case OPEN_SHORT -> new TradeShape(PositionAction.OPEN, PositionSide.SHORT, false);
            case CLOSE, REDUCE -> {
                InvestmentPositionPo position = positionRepository
                        .findByAccountIdAndInstrumentId(accountId, decision.getInstrumentId())
                        .orElseThrow(() -> new InvestmentException(InvestmentErrorCode.INVALID_REQUEST,
                                "Close or reduce decision requires an existing paper position"));
                PositionSide side = PositionSide.valueOf(position.getPositionSide());
                PositionAction action = decision.getAction() == InvestmentAgentAction.CLOSE
                        ? PositionAction.CLOSE : PositionAction.REDUCE;
                yield new TradeShape(action, side, true);
            }
            case HOLD -> throw new InvestmentException(InvestmentErrorCode.FORBIDDEN,
                    "HOLD cannot enter paper trading");
        };
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public record ExecutionOutcome(
            Long intentId,
            InvestmentAgentExecutionStatus executionStatus,
            PaperTradeResult tradeResult
    ) {
    }

    private record TradeShape(PositionAction positionAction, PositionSide positionSide, boolean reduceOnly) {
    }
}
