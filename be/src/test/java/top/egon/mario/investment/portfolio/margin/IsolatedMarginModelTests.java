package top.egon.mario.investment.portfolio.margin;

import org.junit.jupiter.api.Test;
import top.egon.mario.investment.common.model.PositionSide;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IsolatedMarginModelTests {

    private final IsolatedMarginModel model = new IsolatedMarginModel();
    private final PositionTierResolver tierResolver = new PositionTierResolver();

    @Test
    void calculatesLinearUsdtNotionalMarginAndLongShortPnlExactly() {
        assertDecimal(model.notional(decimal("2"), decimal("100"), decimal("1")), "200");
        assertDecimal(model.initialMargin(decimal("200"), decimal("5")), "40");
        assertDecimal(model.unrealizedPnl(PositionSide.LONG, decimal("100"), decimal("110"),
                decimal("2"), decimal("1")), "20");
        assertDecimal(model.unrealizedPnl(PositionSide.SHORT, decimal("100"), decimal("110"),
                decimal("2"), decimal("1")), "-20");
        assertDecimal(model.maintenanceMargin(decimal("200"), decimal("0.005")), "1.000");
    }

    @Test
    void selectsHalfOpenPositionTierAtTheNotionalBoundary() {
        List<PositionTier> tiers = tiers();

        assertThat(tierResolver.resolve(decimal("999.99"), tiers).tierLevel()).isEqualTo(1);
        assertThat(tierResolver.resolve(decimal("1000"), tiers).tierLevel()).isEqualTo(2);
        assertDecimal(tierResolver.resolve(decimal("1000"), tiers).maintenanceMarginRate(), "0.01");
    }

    @Test
    void leverageCapUsesTheMostRestrictiveCodeAndMarketLimit() {
        assertDecimal(model.maximumAllowedLeverage(decimal("25"), decimal("20"),
                decimal("15"), decimal("10")), "10");
        assertThatThrownBy(() -> model.maximumAllowedLeverage(decimal("25"), null,
                decimal("15"), decimal("10")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leverage");
    }

    @Test
    void missingOrGappedTierFailsClosed() {
        assertThatThrownBy(() -> tierResolver.resolve(decimal("100"), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tier");
        assertThatThrownBy(() -> tierResolver.resolve(decimal("1500"), List.of(
                new PositionTier(1, decimal("0"), decimal("1000"), decimal("20"), decimal("0.005")),
                new PositionTier(2, decimal("2000"), decimal("5000"), decimal("10"), decimal("0.01")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No position tier");
    }

    private static List<PositionTier> tiers() {
        return List.of(
                new PositionTier(1, decimal("0"), decimal("1000"), decimal("20"), decimal("0.005")),
                new PositionTier(2, decimal("1000"), decimal("5000"), decimal("10"), decimal("0.01")));
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static void assertDecimal(BigDecimal actual, String expected) {
        assertThat(actual).isEqualByComparingTo(expected);
    }
}
