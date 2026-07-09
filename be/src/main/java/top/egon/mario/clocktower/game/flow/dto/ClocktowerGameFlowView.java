package top.egon.mario.clocktower.game.flow.dto;

import java.util.List;
import java.util.Map;

public record ClocktowerGameFlowView(
        Long gameId,
        String status,
        String phase,
        int dayNo,
        int nightNo,
        boolean advanceAllowed,
        List<String> blockingReasons,
        String nextPhase,
        Map<String, Object> counters
) {
}
