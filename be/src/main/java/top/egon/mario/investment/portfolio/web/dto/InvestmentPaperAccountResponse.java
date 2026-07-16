package top.egon.mario.investment.portfolio.web.dto;

import java.time.Instant;

/**
 * Server-calculated paper account facts; decimal values remain lossless strings.
 */
public record InvestmentPaperAccountResponse(
        Long id,
        Long workspaceId,
        String name,
        String baseCurrency,
        String initialEquity,
        String walletBalance,
        String equity,
        String usedMargin,
        String availableBalance,
        String grossExposure,
        String unrealizedPnl,
        boolean tradingEnabled,
        boolean agentAutoTradeEnabled,
        String status,
        Instant openedAt,
        Long version
) {
}
