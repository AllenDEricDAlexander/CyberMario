package top.egon.mario.investment.quant.strategy.fixture;

import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.quant.strategy.InvestmentStrategy;
import top.egon.mario.investment.quant.strategy.StrategyContext;
import top.egon.mario.investment.quant.strategy.StrategyDecision;
import top.egon.mario.investment.quant.strategy.StrategyDescriptor;
import top.egon.mario.investment.quant.strategy.StrategyEngineType;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Test-only code strategy; production intentionally registers no private strategy.
 */
public final class TestEmaCrossStrategy implements InvestmentStrategy {

    public static final StrategyDescriptor DESCRIPTOR = new StrategyDescriptor(
            "TEST_EMA_CROSS",
            "1.0.0",
            "Test EMA Cross",
            "Fixed test strategy used to validate the code-only registry.",
            StrategyEngineType.JAVA,
            Set.of(DataCapability.MARKET_CANDLE, DataCapability.MARK_CANDLE,
                    DataCapability.FUNDING_RATE, DataCapability.POSITION_TIER),
            Set.of(BarInterval.M1),
            BarInterval.M1,
            PriceType.MARKET,
            "ON_BAR_CLOSE",
            "FIXED_FRACTION_10_PERCENT",
            new BigDecimal("3"),
            new BigDecimal("5"),
            "CONTRACT_RATE_V1",
            "FIXED_BPS_5",
            "NEXT_BAR_V1"
    );

    @Override
    public StrategyDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public StrategyDecision evaluate(StrategyContext context) {
        return StrategyDecision.hold(context.evaluationTime(), "test fixture");
    }
}
