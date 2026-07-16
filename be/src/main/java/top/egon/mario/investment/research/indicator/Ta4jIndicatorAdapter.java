package top.egon.mario.investment.research.indicator;

import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.Num;
import top.egon.mario.investment.common.web.InvestmentDecimalCodec;
import top.egon.mario.investment.marketdata.web.dto.InvestmentCandleResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Module-private Ta4j boundary. No Ta4j type is exposed by the research service or web API.
 */
@Component
public class Ta4jIndicatorAdapter {

    public List<InvestmentIndicatorPoint> calculate(List<InvestmentCandleResponse> candles) {
        if (candles.isEmpty()) {
            return List.of();
        }
        BarSeries series = toSeries(candles);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator sma20 = new SMAIndicator(close, 20);
        EMAIndicator ema20 = new EMAIndicator(close, 20);
        RSIIndicator rsi14 = new RSIIndicator(close, 14);
        MACDIndicator macd = new MACDIndicator(close, 12, 26);
        Indicator<Num> macdSignal = macd.getSignalLine(9);
        Indicator<Num> macdHistogram = macd.getHistogram(9);
        SMAIndicator bollingerSma = new SMAIndicator(close, 20);
        BollingerBandsMiddleIndicator bollingerMiddle = new BollingerBandsMiddleIndicator(bollingerSma);
        StandardDeviationIndicator deviation = StandardDeviationIndicator.ofPopulation(close, 20);
        BollingerBandsUpperIndicator bollingerUpper = new BollingerBandsUpperIndicator(
                bollingerMiddle, deviation, series.numFactory().two());
        BollingerBandsLowerIndicator bollingerLower = new BollingerBandsLowerIndicator(
                bollingerMiddle, deviation, series.numFactory().two());
        ATRIndicator atr14 = new ATRIndicator(series, 14);

        List<InvestmentIndicatorPoint> points = new ArrayList<>(candles.size());
        for (int index = 0; index < candles.size(); index++) {
            InvestmentCandleResponse candle = candles.get(index);
            points.add(new InvestmentIndicatorPoint(
                    candle.openTime(), candle.close(), value(sma20, index), value(ema20, index),
                    value(rsi14, index), value(macd, index), value(macdSignal, index),
                    value(macdHistogram, index), value(bollingerUpper, index),
                    value(bollingerMiddle, index), value(bollingerLower, index), value(atr14, index)));
        }
        return List.copyOf(points);
    }

    private BarSeries toSeries(List<InvestmentCandleResponse> candles) {
        BarSeries series = new BaseBarSeriesBuilder().withName("investment-closed-bars").build();
        candles.forEach(candle -> series.barBuilder()
                .beginTime(candle.openTime())
                .endTime(candle.closeTime())
                .openPrice(candle.open())
                .highPrice(candle.high())
                .lowPrice(candle.low())
                .closePrice(candle.close())
                .volume(candle.baseVolume())
                .amount(candle.quoteVolume())
                .add());
        return series;
    }

    private String value(Indicator<Num> indicator, int index) {
        if (index < indicator.getCountOfUnstableBars()) {
            return null;
        }
        Num value = indicator.getValue(index);
        if (value == null || value.isNaN()) {
            return null;
        }
        BigDecimal decimal = value.bigDecimalValue();
        return InvestmentDecimalCodec.format(decimal);
    }
}
