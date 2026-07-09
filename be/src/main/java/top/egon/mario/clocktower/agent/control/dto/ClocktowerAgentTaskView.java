package top.egon.mario.clocktower.agent.control.dto;

import java.time.Instant;
import java.util.Map;

public record ClocktowerAgentTaskView(
        Long taskId,
        Long gameId,
        Long agentInstanceId,
        Long gameSeatId,
        String triggerType,
        String triggerKey,
        String status,
        int priority,
        Instant availableAt,
        Instant lockedAt,
        String lockedBy,
        int attempts,
        String lastError,
        Map<String, Object> metadata,
        Map<String, Object> result
) {
}
