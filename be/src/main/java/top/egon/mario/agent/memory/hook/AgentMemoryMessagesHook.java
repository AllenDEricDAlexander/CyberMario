package top.egon.mario.agent.memory.hook;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Injects preassembled memory prompt fragments into model calls.
 */
@Component
@HookPositions(HookPosition.BEFORE_MODEL)
public class AgentMemoryMessagesHook extends MessagesModelHook {

    public static final String SAFETY_PROMPT_METADATA_KEY = "agentSafetyPrompt";
    public static final String SOUL_PROMPT_METADATA_KEY = "agentSoulPrompt";
    public static final String LONG_TERM_MEMORY_METADATA_KEY = "agentLongTermMemory";
    public static final String SHORT_TERM_MEMORY_METADATA_KEY = "agentRecentTurns";
    public static final String LONG_TERM_PROMPT_METADATA = "agentMemoryLongTermPrompt";
    public static final String SHORT_TERM_PROMPT_METADATA = "agentMemoryShortTermPrompt";
    public static final String INJECTED_CONTEXT_METADATA_KEY = "agentInjectedContext";

    private static final String LEGACY_LONG_TERM_MEMORY_METADATA_KEY = "longTermMemory";
    private static final String LEGACY_SHORT_TERM_MEMORY_METADATA_KEY = "shortTermMemory";

    @Override
    public String getName() {
        return "agentMemoryMessages";
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        List<Message> baseMessages = previousMessages == null ? List.of() : previousMessages;
        List<Message> retainedMessages = retainedMessages(baseMessages);
        String safetyPrompt = metadataString(config, SAFETY_PROMPT_METADATA_KEY);
        String soulPrompt = metadataString(config, SOUL_PROMPT_METADATA_KEY);
        String longTermPrompt = firstMetadataString(config,
                LONG_TERM_MEMORY_METADATA_KEY, LONG_TERM_PROMPT_METADATA, LEGACY_LONG_TERM_MEMORY_METADATA_KEY);
        String shortTermPrompt = firstMetadataString(config,
                SHORT_TERM_MEMORY_METADATA_KEY, SHORT_TERM_PROMPT_METADATA, LEGACY_SHORT_TERM_MEMORY_METADATA_KEY);
        if (!StringUtils.hasText(safetyPrompt) && !StringUtils.hasText(soulPrompt)
                && !StringUtils.hasText(longTermPrompt) && !StringUtils.hasText(shortTermPrompt)) {
            return retainedMessages.size() == baseMessages.size()
                    ? new AgentCommand(baseMessages)
                    : new AgentCommand(retainedMessages, UpdatePolicy.REPLACE);
        }
        List<Message> updated = new ArrayList<>();
        if (StringUtils.hasText(safetyPrompt)) {
            updated.add(contextMessage(safetyPrompt));
        }
        if (StringUtils.hasText(soulPrompt)) {
            updated.add(contextMessage(soulPrompt));
        }
        if (StringUtils.hasText(longTermPrompt)) {
            updated.add(contextMessage(longTermPrompt));
        }
        if (StringUtils.hasText(shortTermPrompt)) {
            updated.add(contextMessage(shortTermPrompt));
        }
        updated.addAll(retainedMessages);
        return new AgentCommand(updated, UpdatePolicy.REPLACE);
    }

    private List<Message> retainedMessages(List<Message> previousMessages) {
        return previousMessages.stream()
                .filter(message -> !isInjectedContextMessage(message))
                .toList();
    }

    private boolean isInjectedContextMessage(Message message) {
        return message instanceof SystemMessage
                && Boolean.TRUE.equals(message.getMetadata().get(INJECTED_CONTEXT_METADATA_KEY));
    }

    private SystemMessage contextMessage(String prompt) {
        return SystemMessage.builder()
                .text(prompt)
                .metadata(Map.of(INJECTED_CONTEXT_METADATA_KEY, true))
                .build();
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
