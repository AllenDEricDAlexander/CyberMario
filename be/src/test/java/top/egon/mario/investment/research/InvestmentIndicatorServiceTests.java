package top.egon.mario.investment.research;

import org.junit.jupiter.api.Test;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.query.InvestmentMarketQueryService;
import top.egon.mario.investment.marketdata.web.dto.InvestmentCandleResponse;
import top.egon.mario.investment.research.indicator.InvestmentIndicatorService;
import top.egon.mario.investment.research.indicator.Ta4jIndicatorAdapter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies deterministic indicators use only closed facts visible at one cutoff.
 */
class InvestmentIndicatorServiceTests {

    private static final Instant START = Instant.parse("2030-01-01T00:00:00Z");
    private static final Instant CUTOFF = START.plusSeconds(60 * 60);

    @Test
    void filtersOpenAndFutureBarsBeforeTa4jCalculation() {
        InvestmentMarketQueryService market = mock(InvestmentMarketQueryService.class);
        List<InvestmentCandleResponse> source = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            source.add(candle(index, Integer.toString(index + 1), true, CUTOFF));
        }
        source.add(candle(40, "999", false, CUTOFF));
        source.add(candle(60, "1000", true, CUTOFF));
        when(market.candles(42L, PriceType.MARK, BarInterval.M1,
                START, CUTOFF, CUTOFF, 2_000)).thenReturn(source);
        InvestmentIndicatorService service = new InvestmentIndicatorService(market, new Ta4jIndicatorAdapter());

        var result = service.calculate(42L, PriceType.MARK, BarInterval.M1, START, CUTOFF, CUTOFF);

        assertThat(result.points()).hasSize(40);
        assertThat(result.points().getLast().close()).isEqualTo("40");
        assertThat(new BigDecimal(result.points().getLast().sma20())).isEqualByComparingTo("30.5");
        assertThat(result.points()).allMatch(point -> !point.openTime().isAfter(CUTOFF));
        assertThat(result.revisions()).hasSize(40).containsOnly(1L);
        verify(market).candles(42L, PriceType.MARK, BarInterval.M1,
                START, CUTOFF, CUTOFF, 2_000);
    }

    @Test
    void returnsTheSameValuesAndHashForTheSameFrozenBars() {
        InvestmentMarketQueryService market = mock(InvestmentMarketQueryService.class);
        List<InvestmentCandleResponse> candles = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            candles.add(candle(index, "100", true, CUTOFF));
        }
        when(market.candles(7L, PriceType.MARKET, BarInterval.M1,
                START, CUTOFF, CUTOFF, 2_000)).thenReturn(candles);
        InvestmentIndicatorService service = new InvestmentIndicatorService(market, new Ta4jIndicatorAdapter());

        var first = service.calculate(7L, PriceType.MARKET, BarInterval.M1, START, CUTOFF, CUTOFF);
        var second = service.calculate(7L, PriceType.MARKET, BarInterval.M1, START, CUTOFF, CUTOFF);

        assertThat(second).isEqualTo(first);
        assertThat(first.inputHash()).hasSize(64);
        assertThat(new BigDecimal(first.points().getLast().sma20())).isEqualByComparingTo("100");
        assertThat(new BigDecimal(first.points().getLast().ema20())).isEqualByComparingTo("100");
    }

    @Test
    void reportsAnExplicitCapabilityFailureWhenTheFixedIndicatorSetLacksHistory() {
        InvestmentMarketQueryService market = mock(InvestmentMarketQueryService.class);
        List<InvestmentCandleResponse> insufficient = new ArrayList<>();
        for (int index = 0; index < 34; index++) {
            insufficient.add(candle(index, "1", true, CUTOFF));
        }
        when(market.candles(9L, PriceType.INDEX, BarInterval.M1,
                START, CUTOFF, CUTOFF, 2_000)).thenReturn(insufficient);
        InvestmentIndicatorService service = new InvestmentIndicatorService(market, new Ta4jIndicatorAdapter());

        assertThatThrownBy(() -> service.calculate(
                9L, PriceType.INDEX, BarInterval.M1, START, CUTOFF, CUTOFF))
                .isInstanceOf(InvestmentException.class)
                .satisfies(error -> assertThat(((InvestmentException) error).getErrorCode())
                        .isEqualTo(InvestmentErrorCode.CAPABILITY_UNAVAILABLE));
    }

    private InvestmentCandleResponse candle(int minute, String close, boolean closed, Instant dataAsOf) {
        Instant open = START.plusSeconds(minute * 60L);
        return new InvestmentCandleResponse(open, open.plusSeconds(60), close, close, close, close,
                "1", "1", closed, 1L, dataAsOf);
    }
}
