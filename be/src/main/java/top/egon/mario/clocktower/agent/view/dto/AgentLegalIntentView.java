package top.egon.mario.clocktower.agent.view.dto;

import java.util.Map;

public record AgentLegalIntentView(
        String intentType,
        Long taskId,
        Long nominationId,
        Boolean voteValue,
        Map<String, Object> payload
) {
}
