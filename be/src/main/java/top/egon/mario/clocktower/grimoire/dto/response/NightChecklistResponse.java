package top.egon.mario.clocktower.grimoire.dto.response;

import java.util.List;

public record NightChecklistResponse(
        int nightNo,
        String nightType,
        List<NightStepResponse> steps,
        boolean completed
) {
}
