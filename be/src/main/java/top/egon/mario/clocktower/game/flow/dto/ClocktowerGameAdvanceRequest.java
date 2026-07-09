package top.egon.mario.clocktower.game.flow.dto;

import java.util.Map;

public record ClocktowerGameAdvanceRequest(
        String targetPhase,
        String reason,
        Map<String, Object> metadata
) {
}
