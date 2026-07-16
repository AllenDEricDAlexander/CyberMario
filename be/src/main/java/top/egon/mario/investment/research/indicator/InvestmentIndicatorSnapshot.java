package top.egon.mario.investment.research.indicator;

import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;

import java.time.Instant;
import java.util.List;

/**
 * Immutable indicator result and the exact market-data boundary used to calculate it.
 */
public record InvestmentIndicatorSnapshot(
        long instrumentId,
        PriceType priceType,
        BarInterval interval,
        Instant dataStartTime,
        Instant dataEndTime,
        Instant dataAsOf,
        String inputHash,
        List<Long> revisions,
        List<InvestmentIndicatorPoint> points
) {

    public InvestmentIndicatorSnapshot {
        revisions = List.copyOf(revisions);
        points = List.copyOf(points);
    }
}
