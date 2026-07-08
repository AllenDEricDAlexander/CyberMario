package top.egon.mario.clocktower.game.mic.dto;

import java.time.Instant;
import java.util.List;

public record ClocktowerMicSessionView(
        Long sessionId,
        Long gameId,
        Integer dayNo,
        String status,
        Long currentHolderGameSeatId,
        Long currentTurnId,
        Instant roundStartedAt,
        Instant roundFinishedAt,
        Instant grabStartedAt,
        Instant grabEndsAt,
        Instant closedAt,
        List<ClocktowerMicTurnView> turns
) {
}
