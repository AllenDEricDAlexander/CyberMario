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
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvestmentProviderModelTests {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-01-01T00:01:00Z");

    @Test
    void normalizedModelsRetainDecimalAndUtcValuesWithoutExchangeDtos() {
        ExternalContract contract = new ContractFixture().create();
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

        assertThat(contract.sourceCode()).isEqualTo("TEST");
        assertThat(contract.productType()).isEqualTo(ProductType.USDT_FUTURES);
        assertThat(contract.symbol()).isEqualTo("BTCUSDT");
        assertThat(contract.contractType()).isEqualTo(ContractType.PERPETUAL);
        assertThat(contract.baseAsset()).isEqualTo("BTC");
        assertThat(contract.quoteAsset()).isEqualTo("USDT");
        assertThat(contract.settleAsset()).isEqualTo("USDT");
        assertThat(contract.pricePrecision()).isEqualTo(2);
        assertThat(contract.quantityPrecision()).isEqualTo(3);
        assertThat(contract.priceEndStep()).isEqualByComparingTo("0.10");
        assertThat(contract.quantityStep()).isEqualByComparingTo("0.001");
        assertThat(contract.contractMultiplier()).isEqualByComparingTo("0.0010");
        assertThat(contract.minTradeQuantity()).isEqualByComparingTo("0.001");
        assertThat(contract.minTradeNotional()).isEqualByComparingTo("5.00");
        assertThat(contract.maxMarketOrderQuantity()).isEqualByComparingTo("100.000");
        assertThat(contract.maxLimitOrderQuantity()).isEqualByComparingTo("500.000");
        assertThat(contract.makerFeeRate()).isEqualByComparingTo("0.000200");
        assertThat(contract.takerFeeRate()).isEqualByComparingTo("0.000600");
        assertThat(contract.minLeverage()).isEqualByComparingTo("1.00");
        assertThat(contract.maxLeverage()).isEqualByComparingTo("125.00");
        assertThat(contract.fundingIntervalHours()).isEqualTo(8);
        assertThat(contract.buyLimitPriceRatio()).isEqualByComparingTo("1.050000");
        assertThat(contract.sellLimitPriceRatio()).isEqualByComparingTo("0.950000");
        assertThat(contract.observedAt()).isEqualTo(END);
        assertThat(ticker.markPrice()).isEqualByComparingTo("100.00");
        assertThat(candle.openTime()).isEqualTo(START);
        assertThat(candle.close()).isEqualByComparingTo("100.00");
        assertThat(fundingRate.rate()).isEqualByComparingTo("0.000100");
        assertThat(positionTier.maintenanceMarginRatio()).isEqualByComparingTo("0.005");
    }

    @Test
    void contractRejectsNullRequiredNumericValues() {
        assertInvalidContract(fixture -> fixture.pricePrecision = null, NullPointerException.class);
        assertInvalidContract(fixture -> fixture.quantityPrecision = null, NullPointerException.class);
        assertInvalidContract(fixture -> fixture.priceEndStep = null, NullPointerException.class);
        assertInvalidContract(fixture -> fixture.quantityStep = null, NullPointerException.class);
        assertInvalidContract(fixture -> fixture.contractMultiplier = null, NullPointerException.class);
        assertInvalidContract(fixture -> fixture.minTradeQuantity = null, NullPointerException.class);
        assertInvalidContract(fixture -> fixture.minTradeNotional = null, NullPointerException.class);
        assertInvalidContract(fixture -> fixture.maxMarketOrderQuantity = null, NullPointerException.class);
        assertInvalidContract(fixture -> fixture.maxLimitOrderQuantity = null, NullPointerException.class);
        assertInvalidContract(fixture -> fixture.makerFeeRate = null, NullPointerException.class);
        assertInvalidContract(fixture -> fixture.takerFeeRate = null, NullPointerException.class);
        assertInvalidContract(fixture -> fixture.minLeverage = null, NullPointerException.class);
        assertInvalidContract(fixture -> fixture.maxLeverage = null, NullPointerException.class);
        assertInvalidContract(fixture -> fixture.fundingIntervalHours = null, NullPointerException.class);
        assertInvalidContract(fixture -> fixture.buyLimitPriceRatio = null, NullPointerException.class);
        assertInvalidContract(fixture -> fixture.sellLimitPriceRatio = null, NullPointerException.class);
    }

    @Test
    void contractRejectsInvalidPrecisionStepsLimitsAndRatios() {
        assertInvalidContract(fixture -> fixture.pricePrecision = -1, IllegalArgumentException.class);
        assertInvalidContract(fixture -> fixture.quantityPrecision = -1, IllegalArgumentException.class);
        assertInvalidContract(fixture -> fixture.priceEndStep = BigDecimal.ZERO, IllegalArgumentException.class);
        assertInvalidContract(fixture -> fixture.quantityStep = new BigDecimal("-0.001"),
                IllegalArgumentException.class);
        assertInvalidContract(fixture -> fixture.contractMultiplier = BigDecimal.ZERO,
                IllegalArgumentException.class);
        assertInvalidContract(fixture -> fixture.minTradeQuantity = BigDecimal.ZERO,
                IllegalArgumentException.class);
        assertInvalidContract(fixture -> fixture.minTradeNotional = new BigDecimal("-5.00"),
                IllegalArgumentException.class);
        assertInvalidContract(fixture -> fixture.maxMarketOrderQuantity = BigDecimal.ZERO,
                IllegalArgumentException.class);
        assertInvalidContract(fixture -> fixture.maxLimitOrderQuantity = new BigDecimal("-1.00"),
                IllegalArgumentException.class);
        assertInvalidContract(fixture -> fixture.buyLimitPriceRatio = BigDecimal.ZERO,
                IllegalArgumentException.class);
        assertInvalidContract(fixture -> fixture.sellLimitPriceRatio = new BigDecimal("-0.01"),
                IllegalArgumentException.class);
    }

    @Test
    void contractRejectsInvalidFeesLeverageAndFundingInterval() {
        assertInvalidContract(fixture -> fixture.makerFeeRate = new BigDecimal("-0.000001"),
                IllegalArgumentException.class);
        assertInvalidContract(fixture -> fixture.takerFeeRate = new BigDecimal("-0.000001"),
                IllegalArgumentException.class);
        assertInvalidContract(fixture -> fixture.minLeverage = BigDecimal.ZERO,
                IllegalArgumentException.class);
        assertInvalidContract(fixture -> fixture.maxLeverage = BigDecimal.ZERO,
                IllegalArgumentException.class);
        assertInvalidContract(fixture -> fixture.maxLeverage = new BigDecimal("0.50"),
                IllegalArgumentException.class);
        assertInvalidContract(fixture -> fixture.fundingIntervalHours = 0, IllegalArgumentException.class);
        assertInvalidContract(fixture -> fixture.fundingIntervalHours = -1, IllegalArgumentException.class);
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

    private static void assertInvalidContract(Consumer<ContractFixture> mutation,
                                              Class<? extends Throwable> expectedType) {
        ContractFixture fixture = new ContractFixture();
        mutation.accept(fixture);
        assertThatThrownBy(fixture::create).isInstanceOf(expectedType);
    }

    private static final class ContractFixture {

        private Integer pricePrecision = 2;
        private Integer quantityPrecision = 3;
        private BigDecimal priceEndStep = new BigDecimal("0.10");
        private BigDecimal quantityStep = new BigDecimal("0.001");
        private BigDecimal contractMultiplier = new BigDecimal("0.0010");
        private BigDecimal minTradeQuantity = new BigDecimal("0.001");
        private BigDecimal minTradeNotional = new BigDecimal("5.00");
        private BigDecimal maxMarketOrderQuantity = new BigDecimal("100.000");
        private BigDecimal maxLimitOrderQuantity = new BigDecimal("500.000");
        private BigDecimal makerFeeRate = new BigDecimal("0.000200");
        private BigDecimal takerFeeRate = new BigDecimal("0.000600");
        private BigDecimal minLeverage = new BigDecimal("1.00");
        private BigDecimal maxLeverage = new BigDecimal("125.00");
        private Integer fundingIntervalHours = 8;
        private BigDecimal buyLimitPriceRatio = new BigDecimal("1.050000");
        private BigDecimal sellLimitPriceRatio = new BigDecimal("0.950000");

        private ExternalContract create() {
            return new ExternalContract("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                    ContractType.PERPETUAL, "BTC", "USDT", "USDT", pricePrecision, quantityPrecision,
                    priceEndStep, quantityStep, contractMultiplier, minTradeQuantity, minTradeNotional,
                    maxMarketOrderQuantity, maxLimitOrderQuantity, makerFeeRate, takerFeeRate,
                    minLeverage, maxLeverage, fundingIntervalHours, buyLimitPriceRatio,
                    sellLimitPriceRatio, END);
        }
    }
}
