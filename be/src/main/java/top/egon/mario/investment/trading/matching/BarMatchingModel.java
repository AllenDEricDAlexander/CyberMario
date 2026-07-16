package top.egon.mario.investment.trading.matching;

import top.egon.mario.investment.common.model.OrderType;
import top.egon.mario.investment.trading.matching.model.ContractTerms;
import top.egon.mario.investment.trading.matching.model.FuturesBar;
import top.egon.mario.investment.trading.matching.model.LiquidityRole;
import top.egon.mario.investment.trading.matching.model.MatchResult;
import top.egon.mario.investment.trading.matching.model.MatchingOrder;
import top.egon.mario.investment.trading.matching.model.TradeSide;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Closed-bar matching model with N+1 execution and no partial fills.
 */
public final class BarMatchingModel implements MatchingModel {

    private final SlippageModel slippageModel;
    private final FeeModel feeModel;

    public BarMatchingModel(SlippageModel slippageModel, FeeModel feeModel) {
        this.slippageModel = Objects.requireNonNull(slippageModel, "slippageModel");
        this.feeModel = Objects.requireNonNull(feeModel, "feeModel");
    }

    @Override
    public MatchResult match(MatchingOrder order, FuturesBar bar, ContractTerms contractTerms) {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(bar, "bar");
        Objects.requireNonNull(contractTerms, "contractTerms");
        if (!bar.closed() || bar.openTime().isBefore(order.eligibleAfter())) {
            return MatchResult.waiting(order);
        }

        BigDecimal quantity = contractTerms.roundQuantity(order.quantity());
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity is below the contract quantity step");
        }
        TradeSide tradeSide = order.tradeSide();
        LiquidityRole liquidityRole;
        BigDecimal fillPrice;
        if (order.orderType() == OrderType.MARKET) {
            liquidityRole = LiquidityRole.TAKER;
            fillPrice = contractTerms.roundExecutionPrice(
                    slippageModel.apply(bar.open(), tradeSide), tradeSide);
        } else {
            liquidityRole = LiquidityRole.MAKER;
            BigDecimal limitPrice = contractTerms.roundLimitPrice(order.limitPrice(), tradeSide);
            BigDecimal reference = limitReference(bar, limitPrice, tradeSide);
            if (reference == null) {
                return MatchResult.waiting(order);
            }
            fillPrice = contractTerms.roundExecutionPrice(reference, tradeSide);
        }
        BigDecimal notional = fillPrice.multiply(quantity).multiply(contractTerms.contractMultiplier()).abs();
        BigDecimal fee = feeModel.calculate(notional, liquidityRole);
        return MatchResult.filled(order, bar.openTime(), tradeSide, liquidityRole,
                fillPrice, quantity, notional, fee);
    }

    private BigDecimal limitReference(FuturesBar bar, BigDecimal limitPrice, TradeSide tradeSide) {
        if (tradeSide == TradeSide.BUY) {
            if (bar.open().compareTo(limitPrice) <= 0) {
                return bar.open();
            }
            return bar.low().compareTo(limitPrice) <= 0 ? limitPrice : null;
        }
        if (bar.open().compareTo(limitPrice) >= 0) {
            return bar.open();
        }
        return bar.high().compareTo(limitPrice) >= 0 ? limitPrice : null;
    }
}
