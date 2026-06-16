package top.egon.mario.agent.memory.service.model;

import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;

public record AgentMemorySessionCreate(
        AgentMemoryEntryType entryType,
        String title,
        Boolean memoryEnabled,
        Boolean longTermExtractionEnabled
) {
}
