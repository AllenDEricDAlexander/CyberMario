package top.egon.mario.clocktower.grimoire.dto.response;

import java.util.List;

public record ClocktowerGrimoireResponse(
        Long roomId,
        GamePhaseResponse phase,
        List<GrimoireSeatResponse> seats,
        List<StatusMarkerResponse> markers,
        List<String> reminders,
        List<StorytellerTaskResponse> pendingTasks,
        boolean ruleTraceEnabled
) {
}
