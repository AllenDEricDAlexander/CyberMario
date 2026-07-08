package top.egon.mario.clocktower.game.action.dto;

import java.util.List;
import java.util.Map;

public record GameActionCommand(
        Long gameId,
        Long actorGameSeatId,
        String actionType,
        List<Long> targetGameSeatIds,
        Long nominationId,
        Boolean vote,
        String content,
        Map<String, Object> payload
) {
}
