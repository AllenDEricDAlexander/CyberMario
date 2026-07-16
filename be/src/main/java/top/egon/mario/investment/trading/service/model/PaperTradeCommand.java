package top.egon.mario.investment.trading.service.model;

import top.egon.mario.investment.common.model.OrderType;
import top.egon.mario.investment.common.model.PositionAction;
import top.egon.mario.investment.common.model.PositionSide;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskLimits;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskSource;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Server-validated command shared by manual, strategy, and Agent callers.
 */
public record PaperTradeCommand(
        Long actorId,
        Long accountId,
        Long instrumentId,
        InvestmentRiskSource source,
        String sourceReferenceId,
        String idempotencyKey,
        PositionAction positionAction,
        PositionSide positionSide,
        OrderType orderType,
        BigDecimal quantity,
        BigDecimal requestedNotional,
        BigDecimal leverage,
        BigDecimal limitPrice,
        boolean reduceOnly,
        String reason,
        Instant dataAsOf,
        Instant expiresAt,
        InvestmentRiskLimits callerLimits
) {
    public PaperTradeCommand {
        if (actorId == null || actorId <= 0 || accountId == null || accountId <= 0
                || instrumentId == null || instrumentId <= 0) {
            throw new IllegalArgumentException("Actor, account, and instrument ids must be positive");
        }
        if (source == null || idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("Source and idempotency key are required");
        }
        if (positionAction == null || positionSide == null || orderType == null
                || quantity == null || quantity.signum() <= 0
                || requestedNotional == null || requestedNotional.signum() <= 0
                || leverage == null || leverage.signum() <= 0 || dataAsOf == null) {
            throw new IllegalArgumentException("Complete positive paper trade terms are required");
        }
        if (orderType == OrderType.MARKET && limitPrice != null) {
            throw new IllegalArgumentException("Market orders cannot include a limit price");
        }
        if (orderType == OrderType.LIMIT && (limitPrice == null || limitPrice.signum() <= 0)) {
            throw new IllegalArgumentException("Limit orders require a positive limit price");
        }
        if (positionAction != PositionAction.OPEN && !reduceOnly) {
            throw new IllegalArgumentException("Reduce and close actions must be reduce-only");
        }
        if (expiresAt != null && !expiresAt.isAfter(dataAsOf)) {
            throw new IllegalArgumentException("expiresAt must be after dataAsOf");
        }
        sourceReferenceId = normalize(sourceReferenceId, 128);
        reason = normalize(reason, 2000);
    }

    private static String normalize(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("Paper trade text field exceeds maximum length");
        }
        return normalized;
    }
}
