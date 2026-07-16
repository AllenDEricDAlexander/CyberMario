package top.egon.mario.investment.marketdata.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * Instrument identity, supported dimensions and current contract specification.
 */
public record InvestmentInstrumentDetailResponse(
        Long instrumentId,
        String venueCode,
        String symbol,
        String baseAsset,
        String quoteAsset,
        String settlementAsset,
        String marginAsset,
        String productType,
        String contractType,
        String status,
        Instant launchTime,
        List<String> availableCapabilities,
        List<String> availablePriceTypes,
        List<String> availableIntervals,
        Instant dataAsOf,
        InvestmentFreshnessResponse freshness,
        boolean contractSpecAvailable,
        InvestmentContractSpecResponse contractSpec
) {
}
