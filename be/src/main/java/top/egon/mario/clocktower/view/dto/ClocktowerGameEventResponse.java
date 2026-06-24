package top.egon.mario.clocktower.view.dto;

import top.egon.mario.clocktower.game.po.ClocktowerGameEventPo;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ClocktowerGameEventResponse(
        Long eventId,
        Long gameId,
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

    public static ClocktowerGameEventResponse from(ClocktowerGameEventPo event, List<Long> visibleGameSeatIds,
                                                   Map<String, Object> payload) {
        return new ClocktowerGameEventResponse(event.getId(), event.getGameId(), event.getEventSeq(),
                event.getEventType(), event.getPhase(), event.getDayNo(), event.getNightNo(),
                event.getActorGameSeatId(), event.getTargetGameSeatId(), event.getVisibility(),
                visibleGameSeatIds, payload, event.getOccurredAt());
    }
}
