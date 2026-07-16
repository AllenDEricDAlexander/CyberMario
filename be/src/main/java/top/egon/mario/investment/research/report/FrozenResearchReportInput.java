package top.egon.mario.investment.research.report;

import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;

import java.time.Instant;

/**
 * Canonical user input frozen before a report job is queued.
 */
public record FrozenResearchReportInput(
        InvestmentReportType reportType,
        Long instrumentId,
        PriceType priceType,
        BarInterval interval,
        Instant fromInclusive,
        Instant toExclusive,
        Instant dataAsOf
) {

    public String canonicalValue() {
        return String.join("|", reportType.name(), value(instrumentId), value(priceType), value(interval),
                value(fromInclusive), value(toExclusive), dataAsOf.toString());
    }

    private static String value(Object value) {
        return value == null ? "NONE" : value.toString();
    }
}
