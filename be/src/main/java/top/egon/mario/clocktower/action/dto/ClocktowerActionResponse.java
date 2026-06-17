package top.egon.mario.clocktower.action.dto;

import top.egon.mario.clocktower.event.dto.ClocktowerEventResponse;

public record ClocktowerActionResponse(
        boolean accepted,
        String rejectedCode,
        ClocktowerEventResponse event
) {

    public static ClocktowerActionResponse accepted(ClocktowerEventResponse event) {
        return new ClocktowerActionResponse(true, null, event);
    }

    public static ClocktowerActionResponse rejected(String rejectedCode) {
        return new ClocktowerActionResponse(false, rejectedCode, null);
    }
}
