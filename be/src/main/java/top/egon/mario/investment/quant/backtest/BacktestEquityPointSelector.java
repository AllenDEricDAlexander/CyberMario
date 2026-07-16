package top.egon.mario.investment.quant.backtest;

import org.springframework.stereotype.Component;
import top.egon.mario.investment.quant.backtest.model.BacktestEquityPoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Retains boundary/event points and at most 5,000 evenly spaced non-event samples.
 */
@Component
public class BacktestEquityPointSelector {

    static final int MAX_NON_EVENT_POINTS = 5_000;

    public List<BacktestEquityPoint> select(List<BacktestEquityPoint> points) {
        if (points.size() <= 2) {
            return List.copyOf(points);
        }
        Map<java.time.Instant, BacktestEquityPoint> selected = new LinkedHashMap<>();
        selected.put(points.getFirst().pointTime(), points.getFirst());
        points.stream().filter(BacktestEquityPoint::eventPoint)
                .forEach(point -> selected.put(point.pointTime(), point));
        java.math.BigDecimal maximumDrawdown = java.math.BigDecimal.ZERO;
        for (BacktestEquityPoint point : points) {
            if (point.drawdown().compareTo(maximumDrawdown) > 0) {
                maximumDrawdown = point.drawdown();
                selected.put(point.pointTime(), point);
            }
        }
        List<BacktestEquityPoint> samples = points.stream()
                .filter(point -> !point.eventPoint() && !selected.containsKey(point.pointTime())).toList();
        if (samples.size() <= MAX_NON_EVENT_POINTS) {
            samples.forEach(point -> selected.put(point.pointTime(), point));
        } else {
            for (int index = 0; index < MAX_NON_EVENT_POINTS; index++) {
                int sourceIndex = (int) (((long) index * (samples.size() - 1)) / (MAX_NON_EVENT_POINTS - 1));
                BacktestEquityPoint point = samples.get(sourceIndex);
                selected.put(point.pointTime(), point);
            }
        }
        selected.put(points.getLast().pointTime(), points.getLast());
        return new ArrayList<>(selected.values()).stream()
                .sorted(java.util.Comparator.comparing(BacktestEquityPoint::pointTime)).toList();
    }
}
