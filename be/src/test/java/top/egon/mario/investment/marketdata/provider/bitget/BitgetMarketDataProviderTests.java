package top.egon.mario.investment.marketdata.provider.bitget;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.common.model.ProductType;
import top.egon.mario.investment.marketdata.provider.MarketDataProviderException;
import top.egon.mario.investment.marketdata.provider.ProviderErrorCategory;
import top.egon.mario.investment.marketdata.provider.model.CandleQuery;
import top.egon.mario.investment.marketdata.provider.model.FundingRateQuery;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BitgetMarketDataProviderTests {

    private static final Instant NOW = Instant.parse("2026-07-19T12:00:00Z");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @Test
    void convertsHalfOpenRangeToBitgetBoundsAndReturnsEarliestUniqueCandles() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = start.plus(Duration.ofMinutes(3));
        RecordingTransport transport = new RecordingTransport(response("""
                {"code":"00000","msg":"success","data":[
                  ["1735689780000","99","100","98","99","1","99"],
                  ["1735689720000","98","99","97","98","1","98"],
                  ["1735689660000","97","98","96","97","1","97"],
                  ["1735689600000","96","97","95","96","1","96"],
                  ["1735689600000","96","97","95","96","1","96"]
                ]}
                """));
        BitgetMarketDataProvider provider = provider(transport);

        var candles = provider.candles(new CandleQuery(ProductType.USDT_FUTURES, "BTCUSDT",
                PriceType.MARKET, BarInterval.M1, start, end, 100));

        assertThat(candles).extracting(value -> value.openTime())
                .containsExactly(start, start.plusSeconds(60), start.plusSeconds(120));
        assertThat(candles).allSatisfy(value -> assertThat(value.closed()).isTrue());
        assertThat(transport.uris).singleElement().satisfies(uri -> {
            assertThat(uri.getPath()).isEqualTo("/api/v3/market/history-candles");
            assertThat(uri.getQuery()).contains("startTime=" + (start.toEpochMilli() - 1L))
                    .contains("endTime=" + (end.toEpochMilli() - 1L))
                    .contains("limit=100");
        });
    }

    @Test
    void narrowsTheNextDailyWindowToTheRemainingLimitWithoutSkippingAnchoredHistory() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = start.plus(Duration.ofDays(180));
        AnchoredDailyTransport transport = new AnchoredDailyTransport();
        BitgetMarketDataProvider provider = provider(transport);

        var candles = provider.candles(new CandleQuery(ProductType.USDT_FUTURES, "BTCUSDT",
                PriceType.MARKET, BarInterval.D1, start, end, 100));

        assertThat(candles).extracting(value -> value.openTime())
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, 100)
                        .mapToObj(day -> start.plus(Duration.ofDays(day))).toList());
        assertThat(transport.uris).hasSize(2);
        assertThat(queryLong(transport.uris.get(0), "endTime"))
                .isEqualTo(start.plus(Duration.ofDays(90)).toEpochMilli() - 1L);
        assertThat(queryLong(transport.uris.get(1), "startTime"))
                .isEqualTo(start.plus(Duration.ofDays(90)).toEpochMilli() - 1L);
        assertThat(queryLong(transport.uris.get(1), "endTime"))
                .isEqualTo(start.plus(Duration.ofDays(100)).toEpochMilli() - 1L);
        assertThat(queryLong(transport.uris.get(1), "limit")).isEqualTo(10L);
    }

    @Test
    void keepsSettledFundingHistorySeparateFromCurrentFundingQuote() {
        Instant settledAt = Instant.parse("2026-07-19T08:00:00Z");
        Instant nextAt = Instant.parse("2026-07-19T16:00:00Z");
        RecordingTransport transport = new RecordingTransport(
                response("""
                        {"code":"00000","msg":"success","data":{"resultList":[
                          {"symbol":"BTCUSDT","fundingRate":"0.001","fundingRateTimestamp":"%d"}
                        ],"nextFlag":false}}
                        """.formatted(settledAt.toEpochMilli())),
                response("""
                        {"code":"00000","msg":"success","data":[
                          ["%d","100","101","99","100.5","2","201"]
                        ]}
                        """.formatted(NOW.minusSeconds(60).toEpochMilli())),
                response("""
                        {"code":"00000","msg":"success","data":[
                          {"symbol":"BTCUSDT","fundingRate":"0.009","nextUpdate":"%d"}
                        ]}
                        """.formatted(nextAt.toEpochMilli())));
        BitgetMarketDataProvider provider = provider(transport);

        var history = provider.fundingRates(new FundingRateQuery(ProductType.USDT_FUTURES, "BTCUSDT",
                settledAt.minusSeconds(1), settledAt.plusSeconds(1), 100));
        var ticker = provider.tickers(ProductType.USDT_FUTURES, Set.of("BTCUSDT")).getFirst();

        assertThat(provider.capabilities()).containsExactlyInAnyOrder(
                DataCapability.MARKET_CANDLE, DataCapability.FUNDING_RATE,
                DataCapability.CURRENT_FUNDING_RATE);
        assertThat(history).singleElement().satisfies(rate -> {
            assertThat(rate.rate()).isEqualByComparingTo("0.001");
            assertThat(rate.fundingTime()).isEqualTo(settledAt);
        });
        assertThat(ticker.fundingRate()).isEqualByComparingTo("0.009");
        assertThat(ticker.nextFundingTime()).isEqualTo(nextAt);
        assertThat(transport.uris).extracting(URI::getPath).containsExactly(
                "/api/v3/market/history-fund-rate",
                "/api/v3/market/candles",
                "/api/v3/market/current-fund-rate");
    }

    @Test
    void failsExplicitlyWhenFundingCursorLimitCannotReachRequestedStart() {
        BitgetHttpTransport transport = (uri, timeout) -> response("""
                {"code":"00000","msg":"success","data":{"resultList":[
                  {"symbol":"BTCUSDT","fundingRate":"0.001","fundingRateTimestamp":"1767225600000"}
                ],"nextFlag":true}}
                """);
        BitgetMarketDataProvider provider = provider(transport);

        assertThatThrownBy(() -> provider.fundingRates(new FundingRateQuery(
                ProductType.USDT_FUTURES, "BTCUSDT",
                Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2026-07-19T00:00:00Z"), 100)))
                .isInstanceOf(MarketDataProviderException.class)
                .satisfies(exception -> assertThat(((MarketDataProviderException) exception).getCategory())
                        .isEqualTo(ProviderErrorCategory.NON_RETRYABLE))
                .hasMessageContaining("cursor 100");
    }

    @Test
    void mapsRateLimitAndMalformedPayloadToStableCategories() {
        BitgetMarketDataProvider limited = provider((uri, timeout) ->
                new BitgetHttpTransport.Response(429, "{}"));
        assertThatThrownBy(() -> limited.tickers(ProductType.USDT_FUTURES, Set.of("BTCUSDT")))
                .isInstanceOf(MarketDataProviderException.class)
                .satisfies(exception -> assertThat(((MarketDataProviderException) exception).getCategory())
                        .isEqualTo(ProviderErrorCategory.RATE_LIMITED));

        BitgetMarketDataProvider malformed = provider((uri, timeout) ->
                new BitgetHttpTransport.Response(200, "{broken"));
        assertThatThrownBy(() -> malformed.tickers(ProductType.USDT_FUTURES, Set.of("BTCUSDT")))
                .isInstanceOf(MarketDataProviderException.class)
                .satisfies(exception -> assertThat(((MarketDataProviderException) exception).getCategory())
                        .isEqualTo(ProviderErrorCategory.INVALID_DATA));
    }

    private BitgetMarketDataProvider provider(BitgetHttpTransport transport) {
        return new BitgetMarketDataProvider(OBJECT_MAPPER, Clock.fixed(NOW, ZoneOffset.UTC),
                "https://api.bitget.test", Duration.ofSeconds(2), transport);
    }

    private static BitgetHttpTransport.Response response(String body) {
        return new BitgetHttpTransport.Response(200, body);
    }

    private static long queryLong(URI uri, String name) {
        for (String parameter : uri.getRawQuery().split("&")) {
            int separator = parameter.indexOf('=');
            if (separator > 0 && name.equals(parameter.substring(0, separator))) {
                return Long.parseLong(parameter.substring(separator + 1));
            }
        }
        throw new AssertionError("Missing URI query parameter: " + name);
    }

    private static final class RecordingTransport implements BitgetHttpTransport {

        private final ArrayDeque<Response> responses;
        private final List<URI> uris = new ArrayList<>();

        private RecordingTransport(Response... responses) {
            this.responses = new ArrayDeque<>(List.of(responses));
        }

        @Override
        public Response get(URI uri, Duration timeout) {
            uris.add(uri);
            return responses.removeFirst();
        }
    }

    private static final class AnchoredDailyTransport implements BitgetHttpTransport {

        private final List<URI> uris = new ArrayList<>();

        @Override
        public Response get(URI uri, Duration timeout) {
            uris.add(uri);
            Instant start = Instant.ofEpochMilli(queryLong(uri, "startTime") + 1L);
            Instant end = Instant.ofEpochMilli(queryLong(uri, "endTime") + 1L);
            int limit = Math.toIntExact(queryLong(uri, "limit"));
            List<Instant> available = java.util.stream.Stream.iterate(start, value -> value.isBefore(end),
                    value -> value.plus(Duration.ofDays(1))).toList();
            int first = Math.max(0, available.size() - limit);
            StringBuilder rows = new StringBuilder();
            for (int index = available.size() - 1; index >= first; index--) {
                if (!rows.isEmpty()) {
                    rows.append(',');
                }
                rows.append("[\"").append(available.get(index).toEpochMilli())
                        .append("\",\"100\",\"101\",\"99\",\"100\",\"1\",\"100\"]");
            }
            return response("{\"code\":\"00000\",\"msg\":\"success\",\"data\":[" + rows + "]}");
        }
    }
}
