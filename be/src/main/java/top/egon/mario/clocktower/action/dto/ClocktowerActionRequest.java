package top.egon.mario.clocktower.action.dto;

import java.util.List;
import java.util.Map;

public record ClocktowerActionRequest(
        Long seatId,
        String actionType,
        List<Long> targetSeatIds,
        Long privateThreadId,
        String content,
        Map<String, Object> payload,
        String clientActionId
) {
}
