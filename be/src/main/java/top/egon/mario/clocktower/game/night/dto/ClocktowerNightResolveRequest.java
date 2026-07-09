package top.egon.mario.clocktower.game.night.dto;

import java.util.Map;

public record ClocktowerNightResolveRequest(
        Map<String, Object> result,
        String note
) {
}
