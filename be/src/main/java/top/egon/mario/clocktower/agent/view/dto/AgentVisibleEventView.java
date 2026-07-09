package top.egon.mario.clocktower.agent.view.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AgentVisibleEventView(
        Long eventId,
        Long eventSeq,
        String eventType,
        String phase,
        int dayNo,
        int nightNo,
        Long actorGameSeatId,
        Long targetGameSeatId,
        String visibility,
        List<Long> visibleGameSeatIds,
        Map<String, Object> payload,
        Instant occurredAt
) {
}
