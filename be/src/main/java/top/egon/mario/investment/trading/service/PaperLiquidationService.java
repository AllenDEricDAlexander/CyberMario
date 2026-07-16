package top.egon.mario.investment.trading.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.model.OrderType;
import top.egon.mario.investment.common.model.PositionAction;
import top.egon.mario.investment.common.model.PositionSide;
import top.egon.mario.investment.portfolio.po.InvestmentPositionPo;
import top.egon.mario.investment.portfolio.repository.InvestmentPaperAccountRepository;
import top.egon.mario.investment.portfolio.repository.InvestmentPositionRepository;
import top.egon.mario.investment.trading.matching.FixedBpsSlippageModel;
import top.egon.mario.investment.trading.matching.PaperMatchJobInput;
import top.egon.mario.investment.trading.matching.model.ContractTerms;
import top.egon.mario.investment.trading.matching.model.LiquidityRole;
import top.egon.mario.investment.trading.matching.model.MatchResult;
import top.egon.mario.investment.trading.matching.model.MatchingOrder;
import top.egon.mario.investment.trading.matching.model.TradeSide;
import top.egon.mario.investment.trading.po.InvestmentPaperOrderPo;
import top.egon.mario.investment.trading.po.InvestmentTradeIntentPo;
import top.egon.mario.investment.trading.repository.InvestmentPaperOrderRepository;
import top.egon.mario.investment.trading.repository.InvestmentTradeIntentRepository;
import top.egon.mario.investment.trading.service.model.PaperExecutionResult;
import top.egon.mario.investment.trading.service.model.PaperLiquidationResult;
import top.egon.mario.investment.trading.service.model.PaperMaintenanceMarketSnapshot;
import top.egon.mario.investment.trading.service.model.PaperMarginCheckJobInput;
import top.egon.mario.investment.trading.service.model.PaperTradeResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Creates conservative liquidation intent/order/fill facts through the normal execution seam. */
@Service
public class PaperLiquidationService {

    private final InvestmentPaperAccountRepository accountRepository;
    private final InvestmentPositionRepository positionRepository;
    private final InvestmentPaperOrderRepository orderRepository;
    private final InvestmentTradeIntentRepository intentRepository;
    private final PaperOrderService orderService;
    private final PaperExecutionTransactionService executionService;

    public PaperLiquidationService(
            InvestmentPaperAccountRepository accountRepository,
            InvestmentPositionRepository positionRepository,
            InvestmentPaperOrderRepository orderRepository,
            InvestmentTradeIntentRepository intentRepository,
            PaperOrderService orderService,
            PaperExecutionTransactionService executionService) {
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.orderRepository = orderRepository;
        this.intentRepository = intentRepository;
        this.orderService = orderService;
        this.executionService = executionService;
    }

    @Transactional
    public PaperLiquidationResult liquidate(
            PaperMarginCheckJobInput input, PaperMaintenanceMarketSnapshot market, Instant now) {
        accountRepository.findByIdAndWorkspaceIdForUpdate(input.accountId(), input.workspaceId())
                .orElseThrow(PaperLiquidationService::notFound);
        List<InvestmentPositionPo> positions = positionRepository.findByAccountIdForUpdate(input.accountId());
        InvestmentPositionPo position = positions.stream()
                .filter(value -> value.getId() == input.positionId()
                        && value.getInstrumentId() == input.instrumentId())
                .findFirst().orElse(null);
        if (position == null) {
            return new PaperLiquidationResult(
                    input.accountId(), input.positionId(), null, "POSITION_CLOSED", true);
        }
        String rawKey = "liquidation:%d:%d".formatted(position.getId(), market.marketTime().toEpochMilli());
        PaperTradeResult existing = orderService
                .findByIdempotencyKey(input.accountId(), rawKey).orElse(null);
        if (existing != null) {
            return new PaperLiquidationResult(input.accountId(), input.positionId(),
                    existing.order() == null ? null : existing.order().orderId(),
                    existing.order() == null ? existing.intentStatus() : existing.order().status(), true);
        }
        List<InvestmentPaperOrderPo> pending = orderRepository
                .findPendingByAccountAndInstrumentForUpdate(input.accountId(), input.instrumentId());
        pending.forEach(order -> {
            order.setStatus("CANCELLED");
            order.setCancelledAt(now);
        });
        if (!pending.isEmpty()) {
            orderRepository.saveAllAndFlush(pending);
        }

        PositionSide side = PositionSide.valueOf(position.getPositionSide());
        TradeSide tradeSide = side == PositionSide.LONG ? TradeSide.SELL : TradeSide.BUY;
        BigDecimal quantity = new ContractTerms(
                market.priceStep(), market.quantityStep(), market.contractMultiplier())
                .roundQuantity(position.getQuantity());
        BigDecimal notional = quantity.multiply(market.markPrice()).multiply(market.contractMultiplier()).abs();
        InvestmentTradeIntentPo intent = intentRepository.saveAndFlush(
                intent(input, rawKey, side, tradeSide, quantity, notional, position.getLeverage(), market, now));
        InvestmentPaperOrderPo order = orderRepository.saveAndFlush(
                order(intent, tradeSide, quantity, position.getLeverage(), now));

        ContractTerms terms = new ContractTerms(
                market.priceStep(), market.quantityStep(), market.contractMultiplier());
        BigDecimal slipped = new FixedBpsSlippageModel(market.slippageBps())
                .apply(market.markPrice(), tradeSide);
        BigDecimal fillPrice = terms.roundExecutionPrice(slipped, tradeSide);
        BigDecimal fillNotional = fillPrice.multiply(quantity).multiply(market.contractMultiplier()).abs();
        BigDecimal fee = fillNotional.multiply(market.takerFeeRate());
        MatchingOrder matchingOrder = new MatchingOrder(
                order.getId(), OrderType.MARKET, side, PositionAction.CLOSE,
                quantity, null, input.dataAsOf());
        MatchResult match = MatchResult.filled(
                matchingOrder, market.marketTime().truncatedTo(java.time.temporal.ChronoUnit.MINUTES),
                tradeSide, LiquidityRole.TAKER,
                fillPrice, quantity, fillNotional, fee);
        PaperExecutionResult execution = executionService.execute(
                new PaperMatchJobInput(
                        order.getId(), input.workspaceId(), input.accountId(), input.instrumentId(),
                        input.sourceId(), input.dataAsOf(), market.priceStep(), market.quantityStep(),
                        market.contractMultiplier(), market.takerFeeRate(), market.takerFeeRate(),
                        market.slippageBps(), market.maintenanceMarginRate()),
                match, now);
        return new PaperLiquidationResult(
                input.accountId(), input.positionId(), order.getId(), execution.orderStatus(), false);
    }

    private static InvestmentTradeIntentPo intent(
            PaperMarginCheckJobInput input, String rawKey, PositionSide side, TradeSide tradeSide,
            BigDecimal quantity, BigDecimal notional, BigDecimal leverage,
            PaperMaintenanceMarketSnapshot market, Instant now) {
        InvestmentTradeIntentPo intent = new InvestmentTradeIntentPo();
        intent.setWorkspaceId(input.workspaceId());
        intent.setAccountId(input.accountId());
        intent.setInstrumentId(input.instrumentId());
        intent.setSourceType("SYSTEM");
        intent.setSourceReferenceId("margin-check:" + input.positionId());
        intent.setIdempotencyKey(PaperOrderService.scopedIdempotencyKey(input.accountId(), rawKey));
        intent.setPositionAction("CLOSE");
        intent.setSide(tradeSide.name());
        intent.setOrderType("MARKET");
        intent.setQuantity(quantity);
        intent.setRequestedNotional(notional);
        intent.setLeverage(leverage);
        intent.setReduceOnly(true);
        intent.setReason("ISOLATED_MARGIN_LIQUIDATION");
        intent.setDataAsOf(input.dataAsOf());
        intent.setStatus("ACCEPTED");
        intent.setRiskCheckedAt(now);
        intent.setAcceptedAt(now);
        return intent;
    }

    private static InvestmentPaperOrderPo order(
            InvestmentTradeIntentPo intent, TradeSide tradeSide,
            BigDecimal quantity, BigDecimal leverage, Instant now) {
        InvestmentPaperOrderPo order = new InvestmentPaperOrderPo();
        order.setWorkspaceId(intent.getWorkspaceId());
        order.setAccountId(intent.getAccountId());
        order.setIntentId(intent.getId());
        order.setClientOrderId("LIQ-" + intent.getId());
        order.setInstrumentId(intent.getInstrumentId());
        order.setOrigin("LIQUIDATION");
        order.setPositionAction("CLOSE");
        order.setSide(tradeSide.name());
        order.setOrderType("MARKET");
        order.setTimeInForce("IOC");
        order.setQuantity(quantity);
        order.setRemainingQuantity(quantity);
        order.setLeverage(leverage);
        order.setReduceOnly(true);
        order.setStatus("PENDING_MATCH");
        order.setSubmittedAt(now);
        return order;
    }

    private static InvestmentException notFound() {
        return new InvestmentException(InvestmentErrorCode.NOT_FOUND, "Liquidation scope was not found");
    }
}
