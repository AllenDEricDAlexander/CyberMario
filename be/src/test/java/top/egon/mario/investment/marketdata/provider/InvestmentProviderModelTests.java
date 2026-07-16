package top.egon.mario.investment.marketdata.provider;

import org.junit.jupiter.api.Test;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.ContractType;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.common.model.ProductType;
import top.egon.mario.investment.marketdata.provider.model.CandleQuery;
import top.egon.mario.investment.marketdata.provider.model.ExternalCandle;
import top.egon.mario.investment.marketdata.provider.model.ExternalContract;
import top.egon.mario.investment.marketdata.provider.model.ExternalContractTicker;
import top.egon.mario.investment.marketdata.provider.model.ExternalFundingRate;
import top.egon.mario.investment.marketdata.provider.model.ExternalPositionTier;
import top.egon.mario.investment.marketdata.provider.model.FundingRateQuery;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvestmentProviderModelTests {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-01-01T00:01:00Z");

    @Test
    void normalizedModelsRetainDecimalAndUtcValuesWithoutExchangeDtos() {
        ExternalContract contract = new ExternalContract("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                ContractType.PERPETUAL, "BTC", "USDT", "USDT", new BigDecimal("0.001"),
                new BigDecimal("0.001"), new BigDecimal("0.001"), new BigDecimal("0.10"), 100, END);
        ExternalContractTicker ticker = new ExternalContractTicker("TEST", ProductType.USDT_FUTURES,
                "BTCUSDT", new BigDecimal("100.10"), new BigDecimal("100.00"), new BigDecimal("99.90"),
                new BigDecimal("100.05"), new BigDecimal("100.15"), new BigDecimal("12.345"), END);
        ExternalCandle candle = new ExternalCandle("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                PriceType.MARKET, BarInterval.M1, START, END, new BigDecimal("99.00"),
                new BigDecimal("101.00"), new BigDecimal("98.00"), new BigDecimal("100.00"),
                new BigDecimal("10.500"), new BigDecimal("1050.000"), true, END);
        ExternalFundingRate fundingRate = new ExternalFundingRate("TEST", ProductType.USDT_FUTURES,
                "BTCUSDT", new BigDecimal("0.000100"), END, END);
        ExternalPositionTier positionTier = new ExternalPositionTier("TEST", ProductType.USDT_FUTURES,
                "BTCUSDT", 1, BigDecimal.ZERO, new BigDecimal("50000"), new BigDecimal("0.005"), 100, END);

        assertThat(contract.contractSize()).isEqualByComparingTo("0.001");
        assertThat(ticker.markPrice()).isEqualByComparingTo("100.00");
        assertThat(candle.openTime()).isEqualTo(START);
        assertThat(candle.close()).isEqualByComparingTo("100.00");
        assertThat(fundingRate.rate()).isEqualByComparingTo("0.000100");
        assertThat(positionTier.maintenanceMarginRatio()).isEqualByComparingTo("0.005");
    }

    @Test
    void contractTickerRetainsMissingOptionalValuesWithoutSentinels() {
        ExternalContractTicker ticker = ticker(null, null, null, null, null);

        assertThat(ticker.lastPrice()).isEqualByComparingTo("100.00");
        assertThat(ticker.markPrice()).isNull();
        assertThat(ticker.indexPrice()).isNull();
        assertThat(ticker.bidPrice()).isNull();
        assertThat(ticker.askPrice()).isNull();
        assertThat(ticker.openInterest()).isNull();
        assertThat(ticker.observedAt()).isEqualTo(END);
    }

    @Test
    void contractTickerAllowsOneSidedBookPrices() {
        ExternalContractTicker bidOnly = ticker(null, null, new BigDecimal("99.90"), null, BigDecimal.ZERO);
        ExternalContractTicker askOnly = ticker(null, null, null, new BigDecimal("100.10"), null);

        assertThat(bidOnly.bidPrice()).isEqualByComparingTo("99.90");
        assertThat(bidOnly.askPrice()).isNull();
        assertThat(bidOnly.openInterest()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(askOnly.bidPrice()).isNull();
        assertThat(askOnly.askPrice()).isEqualByComparingTo("100.10");
    }

    @Test
    void contractTickerRejectsInvalidPresentOptionalValues() {
        assertThatThrownBy(() -> ticker(BigDecimal.ZERO, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ticker(null, new BigDecimal("-0.01"), null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ticker(null, null, BigDecimal.ZERO, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ticker(null, null, null, new BigDecimal("-0.01"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ticker(null, null, null, null, new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void contractTickerRejectsCrossedBookWhenBothSidesArePresent() {
        assertThatThrownBy(() -> ticker(null, null, new BigDecimal("100.10"), new BigDecimal("99.90"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bidPrice must not exceed askPrice");
    }

    @Test
    void queryModelsRejectSentinelsAndInvalidTimeWindows() {
        assertThatThrownBy(() -> new CandleQuery(ProductType.USDT_FUTURES, "BTCUSDT", PriceType.NONE,
                BarInterval.M1, START, END, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CandleQuery(ProductType.USDT_FUTURES, "BTCUSDT", PriceType.MARKET,
                BarInterval.NONE, START, END, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FundingRateQuery(ProductType.USDT_FUTURES, "BTCUSDT", END, START, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizedModelsRejectUnnormalizedIdentifiersAndMissingDecimalValues() {
        assertThatThrownBy(() -> new ExternalFundingRate("test", ProductType.USDT_FUTURES, "BTCUSDT",
                new BigDecimal("0.0001"), END, END)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExternalFundingRate("TEST", ProductType.USDT_FUTURES, "btcusdt",
                new BigDecimal("0.0001"), END, END)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExternalFundingRate("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                null, END, END)).isInstanceOf(NullPointerException.class);
    }

    private static ExternalContractTicker ticker(BigDecimal markPrice,
                                                   BigDecimal indexPrice,
                                                   BigDecimal bidPrice,
                                                   BigDecimal askPrice,
                                                   BigDecimal openInterest) {
        return new ExternalContractTicker("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                new BigDecimal("100.00"), markPrice, indexPrice, bidPrice, askPrice, openInterest, END);
    }
}
