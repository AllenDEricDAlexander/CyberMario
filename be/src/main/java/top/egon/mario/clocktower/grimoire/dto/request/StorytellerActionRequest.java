package top.egon.mario.clocktower.grimoire.dto.request;

import java.util.List;
import java.util.Map;

public record StorytellerActionRequest(
        String actionType,
        List<Long> targetSeatIds,
        String note,
        Map<String, Object> payload
) {
}
