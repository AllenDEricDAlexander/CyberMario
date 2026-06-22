package top.egon.mario.agent.memory.hook;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Injects preassembled memory prompt fragments into model calls.
 */
@Component
public class AgentMemoryMessagesHook extends MessagesModelHook {

    public static final String SAFETY_PROMPT_METADATA_KEY = "agentSafetyPrompt";
    public static final String SOUL_PROMPT_METADATA_KEY = "agentSoulPrompt";
    public static final String LONG_TERM_MEMORY_METADATA_KEY = "agentLongTermMemory";
    public static final String SHORT_TERM_MEMORY_METADATA_KEY = "agentRecentTurns";
    public static final String LONG_TERM_PROMPT_METADATA = "agentMemoryLongTermPrompt";
    public static final String SHORT_TERM_PROMPT_METADATA = "agentMemoryShortTermPrompt";

    private static final String LEGACY_LONG_TERM_MEMORY_METADATA_KEY = "longTermMemory";
    private static final String LEGACY_SHORT_TERM_MEMORY_METADATA_KEY = "shortTermMemory";

    @Override
    public String getName() {
        return "agentMemoryMessages";
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        String safetyPrompt = metadataString(config, SAFETY_PROMPT_METADATA_KEY);
        String soulPrompt = metadataString(config, SOUL_PROMPT_METADATA_KEY);
        String longTermPrompt = firstMetadataString(config,
                LONG_TERM_MEMORY_METADATA_KEY, LONG_TERM_PROMPT_METADATA, LEGACY_LONG_TERM_MEMORY_METADATA_KEY);
        String shortTermPrompt = firstMetadataString(config,
                SHORT_TERM_MEMORY_METADATA_KEY, SHORT_TERM_PROMPT_METADATA, LEGACY_SHORT_TERM_MEMORY_METADATA_KEY);
        if (!StringUtils.hasText(safetyPrompt) && !StringUtils.hasText(soulPrompt)
                && !StringUtils.hasText(longTermPrompt) && !StringUtils.hasText(shortTermPrompt)) {
            return new AgentCommand(previousMessages);
        }
        List<Message> updated = new ArrayList<>();
        if (StringUtils.hasText(safetyPrompt)) {
            updated.add(new SystemMessage(safetyPrompt));
        }
        if (StringUtils.hasText(soulPrompt)) {
            updated.add(new SystemMessage(soulPrompt));
        }
        if (StringUtils.hasText(longTermPrompt)) {
            updated.add(new SystemMessage(longTermPrompt));
        }
        if (StringUtils.hasText(shortTermPrompt)) {
            updated.add(new SystemMessage(shortTermPrompt));
        }
        updated.addAll(previousMessages);
        return new AgentCommand(updated, UpdatePolicy.REPLACE);
    }

    private String firstMetadataString(RunnableConfig config, String... keys) {
        for (String key : keys) {
            String value = metadataString(config, key);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String metadataString(RunnableConfig config, String key) {
        return config == null ? null : config.metadata(key)
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .orElse(null);
    }
}
