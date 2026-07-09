package top.egon.mario.clocktower.agent.view.dto;

import java.util.List;
import java.util.Map;

public record AgentPrivateView(
        Long gameId,
        Long agentInstanceId,
        Long myGameSeatId,
        int mySeatNo,
        String phase,
        int dayNo,
        int nightNo,
        String myRoleCode,
        String myDisplayedRoleCode,
        String myAlignment,
        String myRoleType,
        String lifeStatus,
        String publicLifeStatus,
        boolean hasDeadVote,
        List<AgentPublicSeatView> publicSeats,
        List<AgentPublicSeatView> grimoire,
        List<AgentVisibleEventView> visibleEvents,
        List<AgentPrivateInfoView> privateInfos,
        List<AgentMemoryView> memories,
        List<AgentLegalIntentView> legalIntents,
        Map<String, Object> roleSpecificContext
) {
}
