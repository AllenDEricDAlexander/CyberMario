package top.egon.mario.investment.research.indicator;

import java.time.Instant;

/**
 * API-safe deterministic indicator values aligned to one closed market bar.
 */
public record InvestmentIndicatorPoint(
        Instant openTime,
        String close,
        String sma20,
        String ema20,
        String rsi14,
        String macd,
        String macdSignal,
        String macdHistogram,
        String bollingerUpper,
        String bollingerMiddle,
        String bollingerLower,
        String atr14
) {
}
