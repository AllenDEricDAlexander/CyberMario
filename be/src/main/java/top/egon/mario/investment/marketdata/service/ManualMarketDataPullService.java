package top.egon.mario.investment.marketdata.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.job.InvestmentJobEnqueueCommand;
import top.egon.mario.investment.common.job.InvestmentJobEnqueueService;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.common.model.ProductType;
import top.egon.mario.investment.marketdata.ingest.MarketDataJobInput;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;
import top.egon.mario.investment.marketdata.web.dto.ManualMarketDataPullRequest;
import top.egon.mario.investment.marketdata.web.dto.ManualMarketDataPullResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Validates the fixed Bitget manual-import boundary and enqueues work without provider I/O.
 */
@Service
public class ManualMarketDataPullService {

    private static final String SOURCE_CODE = "BITGET";
    private static final int PRIORITY = 100;
    private static final int MAX_ATTEMPTS = 5;
    private static final int PAGE_SIZE = 100;
    private static final Duration MAX_WINDOW = Duration.ofDays(730);
    private static final Set<String> SYMBOLS = Set.of("BTCUSDT", "SOLUSDT");
    private static final Set<BarInterval> CANDLE_INTERVALS = Set.of(BarInterval.M1, BarInterval.D1);

    private final InvestmentMarketSubscriptionRegistry subscriptionRegistry;
    private final InvestmentJobEnqueueService enqueueService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ManualMarketDataPullService(InvestmentMarketSubscriptionRegistry subscriptionRegistry,
                                       InvestmentJobEnqueueService enqueueService,
                                       ObjectMapper objectMapper,
                                       Clock clock) {
        this.subscriptionRegistry = subscriptionRegistry;
        this.enqueueService = enqueueService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public ManualMarketDataPullResponse enqueue(ManualMarketDataPullRequest request) {
        if (request == null) {
            throw invalid("Manual market-data pull request is required");
        }
        String symbol = normalizeSymbol(request.symbol());
        Instant now = clock.instant();
        validateRange(request.startInclusive(), request.endExclusive(), now);
        Shape shape = shape(request.capability(), request.interval());
        if (shape.capability() == DataCapability.MARKET_CANDLE) {
            subscriptionRegistry.requireCandle(SOURCE_CODE, ProductType.USDT_FUTURES, symbol,
                    shape.interval(), PriceType.MARKET);
        } else {
            subscriptionRegistry.requireCapability(SOURCE_CODE, ProductType.USDT_FUTURES, symbol,
                    DataCapability.FUNDING_RATE);
        }
        MarketDataJobInput input = new MarketDataJobInput(SOURCE_CODE, ProductType.USDT_FUTURES, symbol,
                shape.capability(), shape.priceType(), shape.interval(), request.startInclusive(),
                request.endExclusive(), PAGE_SIZE, "MANUAL");
        String key = "manual-market:" + UUID.randomUUID();
        long jobId = enqueueService.enqueue(new InvestmentJobEnqueueCommand(null, shape.jobType(), PRIORITY,
                now, MAX_ATTEMPTS, key, json(input)));
        return new ManualMarketDataPullResponse(jobId, shape.jobType().name(), "PENDING", now);
    }

    private String normalizeSymbol(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("symbol is required");
        }
        String symbol = value.trim().toUpperCase(Locale.ROOT);
        if (!SYMBOLS.contains(symbol)) {
            throw invalid("Only BTCUSDT and SOLUSDT are supported");
        }
        return symbol;
    }

    private void validateRange(Instant startInclusive, Instant endExclusive, Instant now) {
        if (startInclusive == null || endExclusive == null || !startInclusive.isBefore(endExclusive)) {
            throw invalid("A valid UTC half-open range is required");
        }
        if (endExclusive.isAfter(now)) {
            throw invalid("endExclusive must not be in the future");
        }
        if (Duration.between(startInclusive, endExclusive).compareTo(MAX_WINDOW) > 0) {
            throw invalid("Manual market-data range must not exceed two years");
        }
    }

    private Shape shape(DataCapability capability, BarInterval interval) {
        if (capability == DataCapability.MARKET_CANDLE) {
            if (interval == null || !CANDLE_INTERVALS.contains(interval)) {
                throw invalid("MARKET_CANDLE requires interval M1 or D1");
            }
            return new Shape(capability, PriceType.MARKET, interval, InvestmentJobType.BAR_BACKFILL);
        }
        if (capability == DataCapability.FUNDING_RATE) {
            if (interval != null) {
                throw invalid("FUNDING_RATE interval must be omitted");
            }
            return new Shape(capability, PriceType.NONE, BarInterval.NONE,
                    InvestmentJobType.FUNDING_RATE_BACKFILL);
        }
        throw invalid("Only MARKET_CANDLE and FUNDING_RATE can be pulled manually");
    }

    private String json(MarketDataJobInput input) {
        try {
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to encode manual market-data job input", exception);
        }
    }

    private InvestmentException invalid(String message) {
        return new InvestmentException(InvestmentErrorCode.INVALID_REQUEST, message);
    }

    private record Shape(DataCapability capability, PriceType priceType, BarInterval interval,
                         InvestmentJobType jobType) {
    }
}
