package top.egon.mario.clocktower.agent.control.dto;

import java.util.Map;

public record ClocktowerAgentMemoryView(
        Long memoryId,
        Long gameId,
        Long agentInstanceId,
        Long gameSeatId,
        Long sourceEventId,
        Long sourceEventSeq,
        String memoryType,
        String visibility,
        Long subjectGameSeatId,
        Map<String, Object> content,
        int confidence,
        int dayNo,
        int nightNo,
        Map<String, Object> metadata
) {
}
