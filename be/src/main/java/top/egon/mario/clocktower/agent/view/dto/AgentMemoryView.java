package top.egon.mario.clocktower.agent.view.dto;

import java.time.Instant;
import java.util.Map;

public record AgentMemoryView(
        Long memoryId,
        Long sourceEventId,
        Long sourceEventSeq,
        String memoryType,
        Long subjectGameSeatId,
        Map<String, Object> content,
        int confidence,
        int dayNo,
        int nightNo,
        Instant createdAt
) {
}
