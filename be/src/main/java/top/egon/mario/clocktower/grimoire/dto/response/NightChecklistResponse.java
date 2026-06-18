package top.egon.mario.clocktower.grimoire.dto.response;

import top.egon.mario.clocktower.common.enums.ClocktowerNightType;

import java.util.List;

public record NightChecklistResponse(
        int nightNo,
        ClocktowerNightType nightType,
        List<NightStepResponse> steps,
        boolean completed
) {
}
