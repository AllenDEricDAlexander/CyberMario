package top.egon.mario.investment.trading.matching;

import org.junit.jupiter.api.Test;
import top.egon.mario.investment.common.model.OrderType;
import top.egon.mario.investment.common.model.PositionAction;
import top.egon.mario.investment.common.model.PositionSide;
import top.egon.mario.investment.trading.matching.model.ContractTerms;
import top.egon.mario.investment.trading.matching.model.FuturesBar;
import top.egon.mario.investment.trading.matching.model.LiquidityRole;
import top.egon.mario.investment.trading.matching.model.MatchStatus;
import top.egon.mario.investment.trading.matching.model.MatchingOrder;
import top.egon.mario.investment.trading.matching.model.TradeSide;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BarMatchingModelTests {

    private static final Instant SIGNAL_CLOSE = Instant.parse("2026-01-01T00:01:00Z");
    private static final ContractTerms TERMS = new ContractTerms(
            decimal("0.01"), decimal("0.1"), decimal("1"));

    private final BarMatchingModel model = new BarMatchingModel(
            new FixedBpsSlippageModel(decimal("10")),
            new RateFeeModel(decimal("0.0002"), decimal("0.0006")));

    @Test
    void marketOrdersWaitForTheNextClosedBarAndUseTakerSlippage() {
        MatchingOrder order = order(OrderType.MARKET, PositionSide.LONG, PositionAction.OPEN,
                "1.239", null);

        assertThat(model.match(order, bar("2026-01-01T00:00:00Z", true,
                "100", "101", "99", "100"), TERMS).status()).isEqualTo(MatchStatus.WAITING);
        assertThat(model.match(order, bar("2026-01-01T00:01:00Z", false,
                "100", "101", "99", "100"), TERMS).status()).isEqualTo(MatchStatus.WAITING);

        var result = model.match(order, bar("2026-01-01T00:01:00Z", true,
                "100", "101", "99", "100"), TERMS);

        assertThat(result.status()).isEqualTo(MatchStatus.FILLED);
        assertDecimal(result.fillPrice(), "100.10");
        assertDecimal(result.quantity(), "1.2");
        assertDecimal(result.notional(), "120.120");
        assertDecimal(result.fee(), "0.0720720");
        assertThat(result.liquidityRole()).isEqualTo(LiquidityRole.TAKER);
        assertThat(order.tradeSide()).isEqualTo(TradeSide.BUY);
    }

    @Test
    void shortOpenAndLongCloseUseSellSideSlippage() {
        var shortOpen = model.match(order(OrderType.MARKET, PositionSide.SHORT, PositionAction.OPEN,
                        "1", null),
                bar("2026-01-01T00:01:00Z", true, "100", "101", "99", "100"), TERMS);
        var longClose = model.match(order(OrderType.MARKET, PositionSide.LONG, PositionAction.CLOSE,
                        "1", null),
                bar("2026-01-01T00:01:00Z", true, "100", "101", "99", "100"), TERMS);

        assertDecimal(shortOpen.fillPrice(), "99.90");
        assertDecimal(longClose.fillPrice(), "99.90");
        assertThat(shortOpen.tradeSide()).isEqualTo(TradeSide.SELL);
        assertThat(longClose.tradeSide()).isEqualTo(TradeSide.SELL);
    }

    @Test
    void limitOrdersUseOpenImprovementThenLimitCrossingAndMakerFee() {
        MatchingOrder buy = order(OrderType.LIMIT, PositionSide.LONG, PositionAction.OPEN,
                "2", "100.007");
        var improved = model.match(buy, bar("2026-01-01T00:01:00Z", true,
                "99", "102", "98", "101"), TERMS);
        var crossed = model.match(buy, bar("2026-01-01T00:01:00Z", true,
                "101", "102", "99", "101"), TERMS);
        var waiting = model.match(buy, bar("2026-01-01T00:01:00Z", true,
                "101", "102", "100.01", "101"), TERMS);

        assertDecimal(improved.fillPrice(), "99");
        assertDecimal(crossed.fillPrice(), "100.00");
        assertDecimal(crossed.fee(), "0.040000");
        assertThat(crossed.liquidityRole()).isEqualTo(LiquidityRole.MAKER);
        assertThat(waiting.status()).isEqualTo(MatchStatus.WAITING);

        MatchingOrder sell = order(OrderType.LIMIT, PositionSide.SHORT, PositionAction.OPEN,
                "2", "100.003");
        assertDecimal(model.match(sell, bar("2026-01-01T00:01:00Z", true,
                "101", "102", "99", "100"), TERMS).fillPrice(), "101");
        assertDecimal(model.match(sell, bar("2026-01-01T00:01:00Z", true,
                "99", "100.01", "98", "99"), TERMS).fillPrice(), "100.01");
    }

    @Test
    void reduceAndCloseDirectionsAreDerivedWithoutCallerOverrides() {
        assertThat(order(OrderType.MARKET, PositionSide.LONG, PositionAction.REDUCE,
                "1", null).tradeSide()).isEqualTo(TradeSide.SELL);
        assertThat(order(OrderType.MARKET, PositionSide.SHORT, PositionAction.REDUCE,
                "1", null).tradeSide()).isEqualTo(TradeSide.BUY);
        assertThat(order(OrderType.MARKET, PositionSide.SHORT, PositionAction.CLOSE,
                "1", null).tradeSide()).isEqualTo(TradeSide.BUY);
    }

    @Test
    void invalidQuantityOrMissingLimitFailsClosed() {
        assertThatThrownBy(() -> model.match(order(OrderType.MARKET, PositionSide.LONG,
                        PositionAction.OPEN, "0.09", null),
                bar("2026-01-01T00:01:00Z", true, "100", "101", "99", "100"), TERMS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity step");
        assertThatThrownBy(() -> order(OrderType.LIMIT, PositionSide.LONG,
                PositionAction.OPEN, "1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limitPrice");
    }

    private static MatchingOrder order(OrderType type, PositionSide side, PositionAction action,
                                       String quantity, String limitPrice) {
        return new MatchingOrder(1L, type, side, action, decimal(quantity),
                limitPrice == null ? null : decimal(limitPrice), SIGNAL_CLOSE);
    }

    private static FuturesBar bar(String openTime, boolean closed, String open, String high,
                                  String low, String close) {
        Instant start = Instant.parse(openTime);
        return new FuturesBar(start, start.plusSeconds(60), decimal(open), decimal(high),
                decimal(low), decimal(close), closed);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static void assertDecimal(BigDecimal actual, String expected) {
        assertThat(actual).isEqualByComparingTo(expected);
    }
}
