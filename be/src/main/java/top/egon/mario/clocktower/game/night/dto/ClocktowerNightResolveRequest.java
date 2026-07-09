package top.egon.mario.clocktower.game.night.dto;

import java.util.Map;
import java.util.List;

public record ClocktowerNightResolveRequest(
        Map<String, Object> result,
        String note,
        List<Long> targetGameSeatIds,
        Map<String, Object> payload
) {

    public ClocktowerNightResolveRequest(Map<String, Object> result, String note) {
        this(result, note, null, null);
    }
}
