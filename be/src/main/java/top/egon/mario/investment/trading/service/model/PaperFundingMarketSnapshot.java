package top.egon.mario.investment.trading.service.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Funding and mark facts frozen before the account transaction. */
public record PaperFundingMarketSnapshot(
        BigDecimal markPrice,
        BigDecimal fundingRate,
        long fundingRevision,
        BigDecimal contractMultiplier,
        Instant marketTime
) {
}
