package top.egon.mario.agent.context.service.model;

import top.egon.mario.agent.memory.hook.AgentMemoryMessagesHook;

import java.util.LinkedHashMap;
import java.util.Map;

public record AgentContext(
        String safetyPrompt,
        String soulPrompt,
        String longTermMemoryPrompt,
        String recentTurnsPrompt
) {

    public Map<String, Object> toMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(AgentMemoryMessagesHook.SAFETY_PROMPT_METADATA_KEY, safe(safetyPrompt));
        if (hasText(soulPrompt)) {
            metadata.put(AgentMemoryMessagesHook.SOUL_PROMPT_METADATA_KEY, soulPrompt);
        }
        metadata.put(AgentMemoryMessagesHook.LONG_TERM_MEMORY_METADATA_KEY, safe(longTermMemoryPrompt));
        metadata.put(AgentMemoryMessagesHook.SHORT_TERM_MEMORY_METADATA_KEY, safe(recentTurnsPrompt));
        return metadata;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
