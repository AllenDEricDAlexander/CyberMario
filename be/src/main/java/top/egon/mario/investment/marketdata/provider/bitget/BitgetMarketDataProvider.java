package top.egon.mario.investment.marketdata.provider.bitget;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.common.model.ProductType;
import top.egon.mario.investment.marketdata.provider.ContractCandleProvider;
import top.egon.mario.investment.marketdata.provider.ContractTickerProvider;
import top.egon.mario.investment.marketdata.provider.FundingRateProvider;
import top.egon.mario.investment.marketdata.provider.MarketDataProviderException;
import top.egon.mario.investment.marketdata.provider.ProviderErrorCategory;
import top.egon.mario.investment.marketdata.provider.model.CandleQuery;
import top.egon.mario.investment.marketdata.provider.model.ExternalCandle;
import top.egon.mario.investment.marketdata.provider.model.ExternalContractTicker;
import top.egon.mario.investment.marketdata.provider.model.ExternalFundingRate;
import top.egon.mario.investment.marketdata.provider.model.FundingRateQuery;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Adapter for Bitget UTA V3 public candles, settled funding rates, and latest quote funding.
 */
@Component
@ConditionalOnProperty(prefix = "mario.investment.bitget", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class BitgetMarketDataProvider
        implements ContractCandleProvider, FundingRateProvider, ContractTickerProvider {

    public static final String PROVIDER_CODE = "BITGET";
    private static final String CATEGORY = "USDT-FUTURES";
    private static final String SUCCESS_CODE = "00000";
    private static final int HISTORY_LIMIT = 100;
    private static final int MAX_FUNDING_CURSOR = 100;
    private static final Duration MAX_HISTORY_WINDOW = Duration.ofDays(90);

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String baseUrl;
    private final Duration timeout;
    private final BitgetHttpTransport transport;

    @Autowired
    public BitgetMarketDataProvider(
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${mario.investment.bitget.base-url:https://api.bitget.com}") String baseUrl,
            @Value("${mario.investment.bitget.timeout:PT10S}") Duration timeout) {
        this(objectMapper, clock, baseUrl, timeout, new JdkBitgetHttpTransport(timeout));
    }

    public BitgetMarketDataProvider(ObjectMapper objectMapper, Clock clock, String baseUrl, Duration timeout,
                                    BitgetHttpTransport transport) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Bitget base URL is required");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Bitget timeout must be positive");
        }
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.timeout = timeout;
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public Set<DataCapability> capabilities() {
        return Set.of(DataCapability.MARKET_CANDLE, DataCapability.FUNDING_RATE,
                DataCapability.CURRENT_FUNDING_RATE);
    }

    @Override
    public List<ExternalCandle> candles(CandleQuery query) {
        requireProduct(query.productType());
        if (query.priceType() != PriceType.MARKET) {
            throw invalidRequest("Bitget adapter supports MARKET candles only");
        }
        int limit = Math.min(query.limit(), HISTORY_LIMIT);
        Duration interval = intervalDuration(query.interval());
        List<ExternalCandle> values = new ArrayList<>();
        Instant windowStart = query.startInclusive();
        while (windowStart.isBefore(query.endExclusive()) && values.size() < limit) {
            int remaining = limit - values.size();
            Duration maximum = min(MAX_HISTORY_WINDOW, interval.multipliedBy(remaining));
            Instant windowEnd = min(windowStart.plus(maximum), query.endExclusive());
            boolean historical = windowStart.isBefore(clock.instant().minus(MAX_HISTORY_WINDOW));
            List<ExternalCandle> page = candlePage(query, windowStart, windowEnd, remaining, historical);
            if (page.isEmpty() && !historical) {
                page = candlePage(query, windowStart, windowEnd, remaining, true);
            }
            values.addAll(page);
            windowStart = windowEnd;
        }
        return earliestUnique(values, ExternalCandle::openTime, limit);
    }

    @Override
    public List<ExternalFundingRate> fundingRates(FundingRateQuery query) {
        requireProduct(query.productType());
        Map<Instant, ExternalFundingRate> values = new LinkedHashMap<>();
        Instant oldestSeen = null;
        boolean exhausted = false;
        for (int cursor = 1; cursor <= MAX_FUNDING_CURSOR; cursor++) {
            JsonNode data = data(get("/api/v3/market/history-fund-rate", Map.of(
                    "category", CATEGORY,
                    "symbol", query.symbol(),
                    "cursor", Integer.toString(cursor),
                    "limit", Integer.toString(HISTORY_LIMIT))));
            JsonNode resultList = requiredArray(data, "resultList");
            if (resultList.isEmpty()) {
                exhausted = true;
                break;
            }
            for (JsonNode node : resultList) {
                Instant fundingTime = millisField(node, "fundingRateTimestamp");
                oldestSeen = oldestSeen == null || fundingTime.isBefore(oldestSeen) ? fundingTime : oldestSeen;
                if (!fundingTime.isBefore(query.startInclusive())
                        && fundingTime.isBefore(query.endExclusive())) {
                    ExternalFundingRate rate = new ExternalFundingRate(PROVIDER_CODE, query.productType(),
                            requiredText(node, "symbol"), decimalField(node, "fundingRate"), fundingTime,
                            clock.instant());
                    values.putIfAbsent(fundingTime, rate);
                }
            }
            if (oldestSeen != null && !oldestSeen.isAfter(query.startInclusive())) {
                exhausted = true;
                break;
            }
            JsonNode nextFlag = data.get("nextFlag");
            if (nextFlag != null && !nextFlag.asBoolean(true)) {
                exhausted = true;
                break;
            }
        }
        if (!exhausted && (oldestSeen == null || oldestSeen.isAfter(query.startInclusive()))) {
            throw new MarketDataProviderException(PROVIDER_CODE, ProviderErrorCategory.NON_RETRYABLE,
                    "Bitget historical funding pagination reached cursor 100 before the requested start");
        }
        return values.values().stream()
                .sorted(Comparator.comparing(ExternalFundingRate::fundingTime))
                .limit(query.limit())
                .toList();
    }

    @Override
    public List<ExternalContractTicker> tickers(ProductType productType, Set<String> symbols) {
        requireProduct(productType);
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        return symbols.stream().sorted().map(symbol -> ticker(productType, symbol)).toList();
    }

    private ExternalContractTicker ticker(ProductType productType, String symbol) {
        JsonNode candleData = data(get("/api/v3/market/candles", Map.of(
                "category", CATEGORY,
                "symbol", symbol,
                "interval", "1m",
                "type", "market",
                "limit", "1")));
        if (!candleData.isArray() || candleData.isEmpty()) {
            throw invalidData("Bitget latest candle response is empty");
        }
        JsonNode candle = candleData.get(0);
        if (!candle.isArray() || candle.size() < 5) {
            throw invalidData("Bitget latest candle row is malformed");
        }
        Instant candleTime = millisValue(candle.get(0), "candle timestamp");
        BigDecimal lastPrice = decimalValue(candle.get(4), "last price");

        JsonNode fundingData = data(get("/api/v3/market/current-fund-rate", Map.of(
                "category", CATEGORY,
                "symbol", symbol)));
        JsonNode funding = findSymbol(requiredArray(fundingData, null), symbol);
        BigDecimal fundingRate = decimalField(funding, "fundingRate");
        Instant nextFundingTime = millisField(funding, "nextUpdate");
        Instant observedAt = max(candleTime, clock.instant());
        return new ExternalContractTicker(PROVIDER_CODE, productType, symbol, lastPrice,
                null, null, null, null, null, fundingRate, nextFundingTime, observedAt);
    }

    private List<ExternalCandle> candlePage(CandleQuery query, Instant startInclusive, Instant endExclusive,
                                            int limit, boolean historical) {
        String path = historical ? "/api/v3/market/history-candles" : "/api/v3/market/candles";
        int apiLimit = historical ? Math.min(limit, HISTORY_LIMIT) : Math.min(limit, 1000);
        JsonNode data = data(get(path, Map.of(
                "category", CATEGORY,
                "symbol", query.symbol(),
                "interval", interval(query.interval()),
                "startTime", Long.toString(startInclusive.toEpochMilli() - 1L),
                "endTime", Long.toString(endExclusive.toEpochMilli() - 1L),
                "type", "market",
                "limit", Integer.toString(apiLimit))));
        if (!data.isArray()) {
            throw invalidData("Bitget candle data must be an array");
        }
        Instant observedAt = clock.instant();
        Duration interval = intervalDuration(query.interval());
        List<ExternalCandle> values = new ArrayList<>();
        for (JsonNode row : data) {
            if (!row.isArray() || row.size() < 7) {
                throw invalidData("Bitget candle row is malformed");
            }
            Instant openTime = millisValue(row.get(0), "candle timestamp");
            if (openTime.isBefore(startInclusive) || !openTime.isBefore(endExclusive)) {
                continue;
            }
            Instant closeTime = openTime.plus(interval);
            values.add(new ExternalCandle(PROVIDER_CODE, query.productType(), query.symbol(),
                    PriceType.MARKET, query.interval(), openTime, closeTime,
                    decimalValue(row.get(1), "open"), decimalValue(row.get(2), "high"),
                    decimalValue(row.get(3), "low"), decimalValue(row.get(4), "close"),
                    decimalValue(row.get(5), "baseVolume"), decimalValue(row.get(6), "quoteVolume"),
                    !closeTime.isAfter(observedAt), observedAt));
        }
        return earliestUnique(values, ExternalCandle::openTime, limit);
    }

    private JsonNode get(String path, Map<String, String> queryParameters) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + path);
        queryParameters.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> builder.queryParam(entry.getKey(), entry.getValue()));
        URI uri = builder.build().encode().toUri();
        BitgetHttpTransport.Response response = transport.get(uri, timeout);
        if (response.statusCode() == 429) {
            throw providerError(ProviderErrorCategory.RATE_LIMITED, "Bitget rate limit exceeded");
        }
        if (response.statusCode() >= 500) {
            throw providerError(ProviderErrorCategory.RETRYABLE,
                    "Bitget service failed with HTTP " + response.statusCode());
        }
        if (response.statusCode() >= 400) {
            throw providerError(ProviderErrorCategory.NON_RETRYABLE,
                    "Bitget rejected the request with HTTP " + response.statusCode());
        }
        try {
            JsonNode root = objectMapper.readTree(response.body());
            String code = requiredText(root, "code");
            if (!SUCCESS_CODE.equals(code)) {
                String message = root.path("msg").asText("Bitget request failed");
                ProviderErrorCategory category = isRateLimited(code, message)
                        ? ProviderErrorCategory.RATE_LIMITED : ProviderErrorCategory.NON_RETRYABLE;
                throw providerError(category, "Bitget error " + code + ": " + message);
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw new MarketDataProviderException(PROVIDER_CODE, ProviderErrorCategory.INVALID_DATA,
                    "Bitget returned malformed JSON", exception);
        }
    }

    private JsonNode data(JsonNode root) {
        JsonNode data = root.get("data");
        if (data == null || data.isNull()) {
            throw invalidData("Bitget response data is missing");
        }
        return data;
    }

    private JsonNode requiredArray(JsonNode parent, String field) {
        JsonNode value = field == null ? parent : parent.get(field);
        if (value == null || !value.isArray()) {
            throw invalidData("Bitget response array is missing: " + (field == null ? "data" : field));
        }
        return value;
    }

    private JsonNode findSymbol(JsonNode values, String symbol) {
        for (JsonNode value : values) {
            if (symbol.equals(requiredText(value, "symbol"))) {
                return value;
            }
        }
        throw invalidData("Bitget current funding response omitted symbol " + symbol);
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw invalidData("Bitget response field is missing: " + field);
        }
        return value.asText();
    }

    private BigDecimal decimalField(JsonNode node, String field) {
        return decimalValue(node.get(field), field);
    }

    private BigDecimal decimalValue(JsonNode value, String field) {
        if (value == null || value.isNull()) {
            throw invalidData("Bitget decimal field is missing: " + field);
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException exception) {
            throw new MarketDataProviderException(PROVIDER_CODE, ProviderErrorCategory.INVALID_DATA,
                    "Bitget decimal field is invalid: " + field, exception);
        }
    }

    private Instant millisField(JsonNode node, String field) {
        return millisValue(node.get(field), field);
    }

    private Instant millisValue(JsonNode value, String field) {
        if (value == null || value.isNull()) {
            throw invalidData("Bitget timestamp field is missing: " + field);
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(value.asText()));
        } catch (NumberFormatException exception) {
            throw new MarketDataProviderException(PROVIDER_CODE, ProviderErrorCategory.INVALID_DATA,
                    "Bitget timestamp field is invalid: " + field, exception);
        }
    }

    private void requireProduct(ProductType productType) {
        if (productType != ProductType.USDT_FUTURES) {
            throw invalidRequest("Bitget adapter supports USDT_FUTURES only");
        }
    }

    private String interval(BarInterval interval) {
        return switch (interval) {
            case M1 -> "1m";
            case M5 -> "5m";
            case M15 -> "15m";
            case M30 -> "30m";
            case H1 -> "1H";
            case H4 -> "4H";
            case D1 -> "1D";
            case NONE -> throw invalidRequest("Concrete Bitget candle interval is required");
        };
    }

    private Duration intervalDuration(BarInterval interval) {
        return switch (interval) {
            case M1 -> Duration.ofMinutes(1);
            case M5 -> Duration.ofMinutes(5);
            case M15 -> Duration.ofMinutes(15);
            case M30 -> Duration.ofMinutes(30);
            case H1 -> Duration.ofHours(1);
            case H4 -> Duration.ofHours(4);
            case D1 -> Duration.ofDays(1);
            case NONE -> throw invalidRequest("Concrete Bitget candle interval is required");
        };
    }

    private boolean isRateLimited(String code, String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        return "429".equals(code) || normalized.contains("rate limit") || normalized.contains("too many");
    }

    private MarketDataProviderException invalidRequest(String message) {
        return providerError(ProviderErrorCategory.NON_RETRYABLE, message);
    }

    private MarketDataProviderException invalidData(String message) {
        return providerError(ProviderErrorCategory.INVALID_DATA, message);
    }

    private MarketDataProviderException providerError(ProviderErrorCategory category, String message) {
        return new MarketDataProviderException(PROVIDER_CODE, category, message);
    }

    private <T> List<T> earliestUnique(List<T> values, java.util.function.Function<T, Instant> timestamp,
                                       int limit) {
        Map<Instant, T> unique = new LinkedHashMap<>();
        values.stream().sorted(Comparator.comparing(timestamp))
                .forEach(value -> unique.putIfAbsent(timestamp.apply(value), value));
        return unique.values().stream().limit(limit).toList();
    }

    private Duration min(Duration left, Duration right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private Instant min(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    private Instant max(Instant left, Instant right) {
        return left.isAfter(right) ? left : right;
    }
}
