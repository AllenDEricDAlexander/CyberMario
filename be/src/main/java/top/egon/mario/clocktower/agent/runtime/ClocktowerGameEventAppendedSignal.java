package top.egon.mario.clocktower.agent.runtime;

import java.util.Map;

public record ClocktowerGameEventAppendedSignal(
        Long eventId,
        Long gameId,
        long eventSeq,
        String eventType,
        String phase,
        Long actorGameSeatId,
        Long targetGameSeatId,
        Map<String, Object> payload
) {
}
