package top.egon.mario.investment.portfolio.margin;

import org.junit.jupiter.api.Test;
import top.egon.mario.investment.common.model.PositionSide;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FundingModelTests {

    private final FundingModel model = new FundingModel(new IsolatedMarginModel());

    @Test
    void positiveFundingMakesLongPayAndShortReceive() {
        assertDecimal(model.calculate(PositionSide.LONG, decimal("10"), decimal("1000"),
                decimal("1"), decimal("0.0001")).cashFlow(), "-1.0000");
        assertDecimal(model.calculate(PositionSide.SHORT, decimal("10"), decimal("1000"),
                decimal("1"), decimal("0.0001")).cashFlow(), "1.0000");
    }

    @Test
    void negativeFundingReversesLongAndShortCashFlow() {
        assertDecimal(model.calculate(PositionSide.LONG, decimal("10"), decimal("1000"),
                decimal("1"), decimal("-0.0001")).cashFlow(), "1.0000");
        assertDecimal(model.calculate(PositionSide.SHORT, decimal("10"), decimal("1000"),
                decimal("1"), decimal("-0.0001")).cashFlow(), "-1.0000");
    }

    @Test
    void missingFundingOrMarkDataFailsClosed() {
        assertThatThrownBy(() -> model.calculate(PositionSide.LONG, decimal("10"), decimal("1000"),
                decimal("1"), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fundingRate");
        assertThatThrownBy(() -> model.calculate(PositionSide.LONG, decimal("10"), null,
                decimal("1"), decimal("0.0001")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("markPrice");
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static void assertDecimal(BigDecimal actual, String expected) {
        assertThat(actual).isEqualByComparingTo(expected);
    }
}
