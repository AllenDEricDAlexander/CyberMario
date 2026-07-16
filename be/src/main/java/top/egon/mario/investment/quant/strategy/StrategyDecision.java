package top.egon.mario.investment.quant.strategy;

import java.time.Instant;
import java.util.Objects;

/**
 * Strategy signal only; sizing, leverage, fees and slippage remain descriptor-owned.
 */
public record StrategyDecision(
        StrategySignal signal,
        Instant signalTime,
        String rationale
) {

    public StrategyDecision {
        Objects.requireNonNull(signal, "signal");
        Objects.requireNonNull(signalTime, "signalTime");
        rationale = rationale == null ? "" : rationale.trim();
        if (rationale.length() > 1000) {
            throw new IllegalArgumentException("rationale exceeds maximum length");
        }
    }

    public static StrategyDecision hold(Instant signalTime, String rationale) {
        return new StrategyDecision(StrategySignal.HOLD, signalTime, rationale);
    }
}
