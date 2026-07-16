package top.egon.mario.investment.quant.web.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.Set;

/**
 * Strict user input; strategy parameters remain code-owned and cannot be posted.
 */
public class SubmitInvestmentBacktestRequest {

    @NotBlank
    private String strategyCode;
    @NotEmpty
    private Set<@Positive Long> instrumentIds;
    @NotNull
    private Instant startTime;
    @NotNull
    private Instant endTime;
    public String getStrategyCode() {
        return strategyCode;
    }

    public void setStrategyCode(String strategyCode) {
        this.strategyCode = strategyCode;
    }

    public Set<Long> getInstrumentIds() {
        return instrumentIds;
    }

    public void setInstrumentIds(Set<Long> instrumentIds) {
        this.instrumentIds = instrumentIds;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    @JsonAnySetter
    public void rejectUnknown(String fieldName, Object value) {
        throw new IllegalArgumentException("Unknown backtest request field: " + fieldName);
    }
}
