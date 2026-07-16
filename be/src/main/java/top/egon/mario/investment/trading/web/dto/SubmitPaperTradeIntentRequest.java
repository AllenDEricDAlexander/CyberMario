package top.egon.mario.investment.trading.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import top.egon.mario.investment.common.model.OrderType;
import top.egon.mario.investment.common.model.PositionAction;
import top.egon.mario.investment.common.model.PositionSide;

import java.time.Instant;

/**
 * Manual simulation intent. Source, actor, account, and strategy limits remain server-bound.
 */
public record SubmitPaperTradeIntentRequest(
        @NotNull @Positive Long instrumentId,
        @NotNull PositionAction positionAction,
        @NotNull PositionSide positionSide,
        @NotNull OrderType orderType,
        @NotBlank String quantity,
        @NotBlank String requestedNotional,
        @NotBlank String leverage,
        String limitPrice,
        boolean reduceOnly,
        @Size(max = 2000) String reason,
        @NotNull Instant dataAsOf,
        Instant expiresAt,
        @NotBlank @Size(max = 128) String idempotencyKey
) {
}
