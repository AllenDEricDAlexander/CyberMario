package top.egon.mario.investment.marketdata;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.query.InvestmentMarketQueryService;
import top.egon.mario.investment.marketdata.web.InvestmentMarketController;
import top.egon.mario.investment.marketdata.web.dto.InvestmentCandleResponse;
import top.egon.mario.investment.marketdata.web.dto.InvestmentFreshnessResponse;
import top.egon.mario.investment.marketdata.web.dto.InvestmentInstrumentSummaryResponse;
import top.egon.mario.investment.marketdata.web.dto.InvestmentQuoteResponse;

import java.lang.reflect.Parameter;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Locks the public market controller contract to internal ids and decimal strings.
 */
@ExtendWith(MockitoExtension.class)
class InvestmentMarketControllerTests {

    @Mock
    private InvestmentMarketQueryService queryService;

    private InvestmentMarketController controller;

    @BeforeEach
    void setUp() {
        controller = new InvestmentMarketController(queryService);
        ReflectionTestUtils.setField(controller, "blockingScheduler", Schedulers.immediate());
    }

    @Test
    void returnsStablePageEnvelopeWithExplicitCapabilitiesAndFreshness() {
        Instant cutoff = Instant.parse("2030-01-01T00:00:00Z");
        InvestmentInstrumentSummaryResponse instrument = new InvestmentInstrumentSummaryResponse(
                42L, "BITGET", "BTCUSDT", "BTC", "USDT", "ACTIVE",
                "123.450000000000000001", "123.400000000000000001", "0.001000000000",
                cutoff, new InvestmentFreshnessResponse("FRESH", cutoff.minusSeconds(10), 10),
                List.of("LATEST_TICKER", "MARKET_CANDLE"));
        when(queryService.listInstruments(1, 20, "ACTIVE", "SYMBOL_ASC"))
                .thenReturn(new PageResult<>(List.of(instrument), 1, 20, 1, 1));

        StepVerifier.create(controller.instruments(1, 20, "ACTIVE", "SYMBOL_ASC"))
                .assertNext(response -> {
                    assertThat(response.data().records()).containsExactly(instrument);
                    assertThat(response.data().records().getFirst().lastPrice())
                            .isEqualTo("123.450000000000000001");
                    assertThat(response.data().records().getFirst().freshness().status()).isEqualTo("FRESH");
                })
                .verifyComplete();
    }

    @Test
    void delegatesQuoteAndAscendingCandlesOnlyByInternalInstrumentId() {
        Instant first = Instant.parse("2030-01-01T00:00:00Z");
        Instant second = first.plusSeconds(60);
        InvestmentQuoteResponse quote = new InvestmentQuoteResponse(
                42L, "1.000000000000000001", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                first, first, 0, second, new InvestmentFreshnessResponse("FRESH", first, 60));
        List<InvestmentCandleResponse> candles = List.of(candle(first, "1"), candle(second, "2"));
        when(queryService.quote(42L)).thenReturn(quote);
        when(queryService.candles(42L, PriceType.MARKET, BarInterval.M1,
                first, second.plusSeconds(60), null, 100)).thenReturn(candles);

        StepVerifier.create(controller.quote(42L))
                .assertNext(response -> assertThat(response.data().lastPrice())
                        .isEqualTo("1.000000000000000001"))
                .verifyComplete();
        StepVerifier.create(controller.candles(42L, PriceType.MARKET, BarInterval.M1,
                        first, second.plusSeconds(60), null, 100))
                .assertNext(response -> assertThat(response.data())
                        .extracting(InvestmentCandleResponse::openTime).containsExactly(first, second))
                .verifyComplete();

        verify(queryService).quote(42L);
        verify(queryService).candles(42L, PriceType.MARKET, BarInterval.M1,
                first, second.plusSeconds(60), null, 100);
        assertThat(Arrays.stream(InvestmentMarketController.class.getDeclaredMethods())
                .flatMap(method -> Arrays.stream(method.getParameters()))
                .map(Parameter::getName))
                .doesNotContain("externalSymbol", "symbol");
    }

    private InvestmentCandleResponse candle(Instant openTime, String close) {
        return new InvestmentCandleResponse(
                openTime, openTime.plusSeconds(60), "1", "2", "1", close,
                "0", "0", true, 1, Instant.parse("2030-01-02T00:00:00Z"));
    }
}
