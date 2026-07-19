package top.egon.mario.investment.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.ContractType;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.InvestmentJobStatus;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.common.model.InvestmentOrderStatus;
import top.egon.mario.investment.common.model.InvestmentRunStatus;
import top.egon.mario.investment.common.model.MarginMode;
import top.egon.mario.investment.common.model.OrderType;
import top.egon.mario.investment.common.model.PositionAction;
import top.egon.mario.investment.common.model.PositionMode;
import top.egon.mario.investment.common.model.PositionSide;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.common.model.ProductType;
import top.egon.mario.investment.common.model.TradeIntentStatus;
import top.egon.mario.investment.config.InvestmentProperties;
import top.egon.mario.investment.common.web.InvestmentDecimalCodec;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvestmentCommonContractTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void freezesExactEnumWireValues() throws Exception {
        assertEnumWireValues(ProductType.class, "USDT_FUTURES");
        assertEnumWireValues(ContractType.class, "PERPETUAL");
        assertEnumWireValues(PriceType.class, "MARKET", "MARK", "INDEX", "NONE");
        assertEnumWireValues(BarInterval.class, "M1", "M5", "M15", "M30", "H1", "H4", "D1", "NONE");
        assertEnumWireValues(DataCapability.class,
                "CONTRACT_METADATA", "MARKET_CANDLE", "MARK_CANDLE", "INDEX_CANDLE", "LATEST_TICKER",
                "FUNDING_RATE", "CURRENT_FUNDING_RATE", "POSITION_TIER", "OPEN_INTEREST");
        assertEnumWireValues(InvestmentJobType.class,
                "CONTRACT_SYNC", "POSITION_TIER_SYNC", "BAR_BACKFILL", "BAR_INCREMENTAL", "QUOTE_REFRESH",
                "FUNDING_RATE_BACKFILL", "FUNDING_RATE_INCREMENTAL", "DATA_QUALITY_CHECK", "REPORT_BUILD",
                "BACKTEST_RUN", "PAPER_MATCH", "PAPER_FUNDING_SETTLE", "PAPER_MARGIN_CHECK", "AGENT_ANALYSIS");
        assertEnumWireValues(InvestmentJobStatus.class, "PENDING", "RUNNING", "SUCCEEDED", "FAILED");
        assertEnumWireValues(MarginMode.class, "ISOLATED");
        assertEnumWireValues(PositionMode.class, "ONE_WAY");
        assertEnumWireValues(PositionSide.class, "LONG", "SHORT");
        assertEnumWireValues(PositionAction.class, "OPEN", "REDUCE", "CLOSE");
        assertEnumWireValues(OrderType.class, "MARKET", "LIMIT");
        assertEnumWireValues(InvestmentOrderStatus.class,
                "PENDING_MATCH", "FAILED", "FILLED", "CANCELLED", "EXPIRED", "REJECTED");
        assertEnumWireValues(TradeIntentStatus.class, "RECEIVED", "RISK_REJECTED", "ACCEPTED", "EXPIRED", "FAILED");
        assertEnumWireValues(InvestmentRunStatus.class, "PENDING", "RUNNING", "SUCCEEDED", "FAILED");
    }

    @Test
    void rejectsNonCanonicalEnumWireValues() {
        assertThatThrownBy(() -> objectMapper.readValue("\"usdt_futures\"", ProductType.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> objectMapper.readValue("0", ProductType.class))
                .hasMessageContaining("string");
        assertThatThrownBy(() -> objectMapper.readValue("\"SPOT\"", ProductType.class))
                .hasMessageContaining("SPOT");
    }

    @Test
    void marketDataPlannerIsDisabledByDefaultAndCanBeExplicitlyEnabled() {
        InvestmentProperties defaults = new Binder(new MapConfigurationPropertySource())
                .bindOrCreate("mario.investment", Bindable.of(InvestmentProperties.class));
        InvestmentProperties enabled = new Binder(new MapConfigurationPropertySource(Map.of(
                        "mario.investment.market-data-planner-enabled", "true")))
                .bindOrCreate("mario.investment", Bindable.of(InvestmentProperties.class));

        assertThat(defaults.marketDataPlannerEnabled()).isFalse();
        assertThat(enabled.marketDataPlannerEnabled()).isTrue();
    }

    @Test
    void serializesDecimalsAsPlainStringsWithoutLosingScale() throws Exception {
        DecimalPayload payload = new DecimalPayload(new BigDecimal("12345678901234567890.123400"));

        assertThat(objectMapper.writeValueAsString(payload))
                .isEqualTo("{\"value\":\"12345678901234567890.123400\"}");
    }

    @Test
    void parsesCanonicalDecimalStringsExactly() throws Exception {
        DecimalPayload payload = objectMapper.readValue("{\"value\":\"-0.000100\"}", DecimalPayload.class);

        assertThat(payload.value()).isEqualByComparingTo(new BigDecimal("-0.000100"));
        assertThat(payload.value().scale()).isEqualTo(6);
    }

    @Test
    void rejectsNumbersExponentWhitespaceAndAmbiguousDecimalForms() {
        assertInvalidDecimal("{\"value\":1.25}", "string");
        assertInvalidDecimal("{\"value\":\"1e3\"}", "canonical decimal");
        assertInvalidDecimal("{\"value\":\" 1.0\"}", "canonical decimal");
        assertInvalidDecimal("{\"value\":\"+1.0\"}", "canonical decimal");
        assertInvalidDecimal("{\"value\":\"01.0\"}", "canonical decimal");
        assertInvalidDecimal("{\"value\":\"\"}", "canonical decimal");
    }

    private <E extends Enum<E>> void assertEnumWireValues(Class<E> enumType, String... expectedValues) throws Exception {
        assertThat(enumType.getEnumConstants()).extracting(Enum::name).containsExactly(expectedValues);
        for (String expectedValue : expectedValues) {
            E enumValue = Enum.valueOf(enumType, expectedValue);
            assertThat(objectMapper.writeValueAsString(enumValue)).isEqualTo("\"" + expectedValue + "\"");
            assertThat(objectMapper.readValue("\"" + expectedValue + "\"", enumType)).isSameAs(enumValue);
        }
    }

    private void assertInvalidDecimal(String json, String messageFragment) {
        assertThatThrownBy(() -> objectMapper.readValue(json, DecimalPayload.class))
                .hasMessageContaining(messageFragment);
    }

    private record DecimalPayload(
            @JsonSerialize(using = InvestmentDecimalCodec.Serializer.class)
            @JsonDeserialize(using = InvestmentDecimalCodec.Deserializer.class)
            BigDecimal value
    ) {
    }
}
