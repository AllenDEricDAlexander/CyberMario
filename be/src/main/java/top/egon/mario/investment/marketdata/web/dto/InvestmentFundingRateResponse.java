package top.egon.mario.investment.marketdata.web.dto;

import java.time.Instant;

/**
 * One funding-rate revision at a fixed market cutoff.
 */
public record InvestmentFundingRateResponse(
        Instant fundingTime,
        String fundingRate,
        long revision,
        Instant dataAsOf
) {
}
