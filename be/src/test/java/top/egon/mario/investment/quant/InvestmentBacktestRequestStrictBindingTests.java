package top.egon.mario.investment.quant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import top.egon.mario.investment.quant.web.dto.SubmitInvestmentBacktestRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvestmentBacktestRequestStrictBindingTests {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void acceptsOnlyStrategyInstrumentsAndRange() throws Exception {
        SubmitInvestmentBacktestRequest request = objectMapper.readValue("""
                {
                  "strategyCode":"TEST_EMA_CROSS",
                  "instrumentIds":[11],
                  "startTime":"2035-01-01T00:00:00Z",
                  "endTime":"2035-01-02T00:00:00Z"
                }
                """, SubmitInvestmentBacktestRequest.class);

        assertThat(request.getStrategyCode()).isEqualTo("TEST_EMA_CROSS");
        assertThat(request.getInstrumentIds()).containsExactly(11L);
    }

    @Test
    void rejectsUnknownFieldsIncludingStrategyParameters() {
        assertThatThrownBy(() -> objectMapper.readValue("""
                {
                  "strategyCode":"TEST_EMA_CROSS",
                  "instrumentIds":[11],
                  "startTime":"2035-01-01T00:00:00Z",
                  "endTime":"2035-01-02T00:00:00Z",
                  "parameters":{"fast":5}
                }
                """, SubmitInvestmentBacktestRequest.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parameters");
    }

    @Test
    void rejectsServerManagedSourceAndInitialEquityFields() {
        assertThatThrownBy(() -> objectMapper.readValue("""
                {
                  "strategyCode":"TEST_EMA_CROSS",
                  "sourceId":3,
                  "instrumentIds":[11],
                  "startTime":"2035-01-01T00:00:00Z",
                  "endTime":"2035-01-02T00:00:00Z"
                }
                """, SubmitInvestmentBacktestRequest.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceId");

        assertThatThrownBy(() -> objectMapper.readValue("""
                {
                  "strategyCode":"TEST_EMA_CROSS",
                  "instrumentIds":[11],
                  "startTime":"2035-01-01T00:00:00Z",
                  "endTime":"2035-01-02T00:00:00Z",
                  "initialEquity":"10000"
                }
                """, SubmitInvestmentBacktestRequest.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("initialEquity");
    }
}
