package top.egon.mario.clocktower.event.dto;

import top.egon.mario.clocktower.common.enums.ClocktowerEventType;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerVisibility;
import top.egon.mario.clocktower.event.po.ClocktowerEventPo;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ClocktowerEventResponse(
        Long eventId,
        Long roomId,
        Long seqNo,
        ClocktowerEventType eventType,
        ClocktowerPhase phase,
        int dayNo,
        int nightNo,
        Long actorUserId,
        Long actorSeatId,
        Long targetSeatId,
        ClocktowerVisibility visibility,
        List<Long> visibleSeatIds,
        Map<String, Object> payload,
        Instant createdAt
) {

    public static ClocktowerEventResponse from(ClocktowerEventPo event, List<Long> visibleSeatIds,
                                               Map<String, Object> payload) {
        return new ClocktowerEventResponse(event.getId(), event.getRoomId(), event.getEventSeq(),
                event.getEventType(), event.getPhase(), event.getDayNo(), event.getNightNo(),
                event.getActorUserId(), event.getActorSeatId(), event.getTargetSeatId(), event.getVisibility(),
                visibleSeatIds, payload, event.getCreatedAt());
    }
}
