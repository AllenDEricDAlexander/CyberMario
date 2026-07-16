package top.egon.mario.investment.research.indicator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.query.InvestmentMarketQueryService;
import top.egon.mario.investment.marketdata.web.dto.InvestmentCandleResponse;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Calculates reusable indicators exclusively from closed bars visible at one cutoff.
 */
@Service
public class InvestmentIndicatorService {

    private static final int MAX_INPUT_BARS = 2_000;
    private static final int MINIMUM_INPUT_BARS = 35;

    private final InvestmentMarketQueryService marketQueryService;
    private final Ta4jIndicatorAdapter indicatorAdapter;
    private final Clock clock;

    @Autowired
    public InvestmentIndicatorService(InvestmentMarketQueryService marketQueryService,
                                      Ta4jIndicatorAdapter indicatorAdapter) {
        this(marketQueryService, indicatorAdapter, Clock.systemUTC());
    }

    InvestmentIndicatorService(InvestmentMarketQueryService marketQueryService,
                               Ta4jIndicatorAdapter indicatorAdapter,
                               Clock clock) {
        this.marketQueryService = Objects.requireNonNull(marketQueryService, "marketQueryService");
        this.indicatorAdapter = Objects.requireNonNull(indicatorAdapter, "indicatorAdapter");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public InvestmentIndicatorSnapshot calculate(long instrumentId, PriceType priceType, BarInterval interval,
                                                 Instant fromInclusive, Instant toExclusive, Instant requestedAsOf) {
        Instant dataAsOf = requestedAsOf == null ? clock.instant() : requestedAsOf;
        List<InvestmentCandleResponse> candles = marketQueryService.candles(
                        instrumentId, priceType, interval, fromInclusive, toExclusive, dataAsOf, MAX_INPUT_BARS)
                .stream()
                .filter(InvestmentCandleResponse::isClosed)
                .filter(candle -> !candle.closeTime().isAfter(dataAsOf))
                .filter(candle -> candle.dataAsOf() != null && !candle.dataAsOf().isAfter(dataAsOf))
                .sorted(Comparator.comparing(InvestmentCandleResponse::openTime))
                .toList();
        if (candles.size() < MINIMUM_INPUT_BARS) {
            throw new InvestmentException(InvestmentErrorCode.CAPABILITY_UNAVAILABLE,
                    "At least " + MINIMUM_INPUT_BARS
                            + " closed as-of candles are required for the fixed indicator set");
        }
        String canonicalBars = candles.stream().map(this::canonicalBar).collect(Collectors.joining("\n"));
        return new InvestmentIndicatorSnapshot(
                instrumentId, priceType, interval, candles.getFirst().openTime(), candles.getLast().closeTime(),
                dataAsOf, ResearchHashSupport.sha256(canonicalBars),
                candles.stream().map(InvestmentCandleResponse::revision).toList(),
                indicatorAdapter.calculate(candles));
    }

    private String canonicalBar(InvestmentCandleResponse candle) {
        return String.join("|", candle.openTime().toString(), candle.closeTime().toString(), candle.open(),
                candle.high(), candle.low(), candle.close(), candle.baseVolume(), candle.quoteVolume(),
                Boolean.toString(candle.isClosed()), Long.toString(candle.revision()));
    }
}
