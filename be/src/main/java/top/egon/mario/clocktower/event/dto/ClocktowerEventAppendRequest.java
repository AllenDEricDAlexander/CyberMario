package top.egon.mario.clocktower.event.dto;

import top.egon.mario.clocktower.common.enums.ClocktowerEventType;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerVisibility;

import java.util.List;
import java.util.Map;

public record ClocktowerEventAppendRequest(
        Long roomId,
        ClocktowerEventType eventType,
        ClocktowerPhase phase,
        int dayNo,
        int nightNo,
        Long actorUserId,
        Long actorSeatId,
        Long targetSeatId,
        ClocktowerVisibility visibility,
        List<Long> visibleSeatIds,
        Map<String, Object> payload
) {
}
