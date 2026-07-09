package top.egon.mario.clocktower.agent.view.dto;

import java.time.Instant;
import java.util.Map;

public record AgentPrivateInfoView(
        Long eventId,
        Long eventSeq,
        String roleCode,
        String taskType,
        Map<String, Object> payload,
        Instant occurredAt
) {
}
