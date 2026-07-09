package top.egon.mario.clocktower.agent.control.dto;

import java.util.Map;

public record ClocktowerAgentConsoleView(
        Long agentInstanceId,
        Long actorId,
        Long gameSeatId,
        Integer seatNo,
        String displayName,
        String profileName,
        String status,
        String autoMode,
        String roleCode,
        String alignment,
        String recentTaskStatus,
        String recentTaskTriggerType,
        Map<String, Object> recentTaskResult,
        String recentError
) {
}
