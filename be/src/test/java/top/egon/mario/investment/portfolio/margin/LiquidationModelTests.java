package top.egon.mario.investment.portfolio.margin;

import org.junit.jupiter.api.Test;
import top.egon.mario.investment.common.model.PositionSide;
import top.egon.mario.investment.trading.matching.model.SimulationEventType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiquidationModelTests {

    private final LiquidationModel model = new LiquidationModel(new IsolatedMarginModel());
    private final PositionTier tier = new PositionTier(
            1, decimal("0"), decimal("1000"), decimal("20"), decimal("0.005"));

    @Test
    void liquidatesLongWhenMarginPlusPnlCannotCoverMaintenanceAndCloseFee() {
        LiquidationResult result = model.evaluate(position(PositionSide.LONG), decimal("90"),
                decimal("1"), tier, decimal("0.0006"));

        assertThat(result.liquidationRequired()).isTrue();
        assertDecimal(result.positionEquity(), "0");
        assertDecimal(result.maintenanceMargin(), "0.45000");
        assertDecimal(result.estimatedCloseFee(), "0.054000");
    }

    @Test
    void keepsHealthyPositionAndMirrorsShortPnlDirection() {
        assertThat(model.evaluate(position(PositionSide.LONG), decimal("100"),
                decimal("1"), tier, decimal("0.0006")).liquidationRequired()).isFalse();
        assertThat(model.evaluate(position(PositionSide.SHORT), decimal("110"),
                decimal("1"), tier, decimal("0.0006")).liquidationRequired()).isTrue();
    }

    @Test
    void liquidationHasPriorityOverOrdinaryLimitMatching() {
        assertThat(SimulationEventType.LIQUIDATION.priority())
                .isLessThan(SimulationEventType.ORDER_MATCH.priority());
    }

    @Test
    void missingMarkTierOrFeeFailsClosed() {
        assertThatThrownBy(() -> model.evaluate(position(PositionSide.LONG), null,
                decimal("1"), tier, decimal("0.0006")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("markPrice");
        assertThatThrownBy(() -> model.evaluate(position(PositionSide.LONG), decimal("90"),
                decimal("1"), null, decimal("0.0006")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tier");
        assertThatThrownBy(() -> model.evaluate(position(PositionSide.LONG), decimal("90"),
                decimal("1"), tier, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("closeFeeRate");
    }

    private static IsolatedPosition position(PositionSide side) {
        return new IsolatedPosition(side, decimal("1"), decimal("100"), decimal("10"));
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static void assertDecimal(BigDecimal actual, String expected) {
        assertThat(actual).isEqualByComparingTo(expected);
    }
}
