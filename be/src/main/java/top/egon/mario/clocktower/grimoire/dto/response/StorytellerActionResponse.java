package top.egon.mario.clocktower.grimoire.dto.response;

import top.egon.mario.clocktower.event.dto.ClocktowerEventResponse;

public record StorytellerActionResponse(
        boolean accepted,
        String rejectedCode,
        ClocktowerEventResponse event,
        ClocktowerGrimoireResponse grimoire
) {

    public static StorytellerActionResponse accepted(ClocktowerEventResponse event, ClocktowerGrimoireResponse grimoire) {
        return new StorytellerActionResponse(true, null, event, grimoire);
    }

    public static StorytellerActionResponse rejected(String rejectedCode, ClocktowerGrimoireResponse grimoire) {
        return new StorytellerActionResponse(false, rejectedCode, null, grimoire);
    }
}
