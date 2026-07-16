package top.egon.mario.investment.marketdata.web.dto;

import java.time.Instant;

/**
 * Decimal-safe normalized perpetual contract specification.
 */
public record InvestmentContractSpecResponse(
        int pricePrecision,
        int quantityPrecision,
        String priceEndStep,
        String quantityStep,
        String contractMultiplier,
        String minTradeQuantity,
        String minTradeNotional,
        String maxMarketOrderQuantity,
        String maxLimitOrderQuantity,
        String makerFeeRate,
        String takerFeeRate,
        String minLeverage,
        String maxLeverage,
        int fundingIntervalHours,
        String buyLimitPriceRatio,
        String sellLimitPriceRatio,
        Instant sourceUpdatedAt,
        Instant ingestedAt,
        long revision
) {
}
