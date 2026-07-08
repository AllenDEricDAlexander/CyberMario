package top.egon.mario.clocktower.game.action.dto;

import top.egon.mario.clocktower.view.dto.ClocktowerGameEventResponse;

public record ClocktowerGameActionResponse(
        boolean accepted,
        String rejectedCode,
        ClocktowerGameEventResponse event
) {

    public static ClocktowerGameActionResponse accepted(ClocktowerGameEventResponse event) {
        return new ClocktowerGameActionResponse(true, null, event);
    }

    public static ClocktowerGameActionResponse rejected(String rejectedCode) {
        return new ClocktowerGameActionResponse(false, rejectedCode, null);
    }
}
