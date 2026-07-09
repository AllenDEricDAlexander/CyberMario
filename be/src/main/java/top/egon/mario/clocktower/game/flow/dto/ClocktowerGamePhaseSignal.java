package top.egon.mario.clocktower.game.flow.dto;

import java.util.Map;

public record ClocktowerGamePhaseSignal(
        Long gameId,
        String previousPhase,
        String phase,
        int dayNo,
        int nightNo,
        boolean forced,
        Map<String, Object> metadata
) {
}
