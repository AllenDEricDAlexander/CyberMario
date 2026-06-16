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

    public static final String SHORT_TERM_PROMPT_METADATA = "agentMemoryShortTermPrompt";
    public static final String LONG_TERM_PROMPT_METADATA = "agentMemoryLongTermPrompt";

    @Override
    public String getName() {
        return "agentMemoryMessages";
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        String longTermPrompt = metadataString(config, LONG_TERM_PROMPT_METADATA);
        String shortTermPrompt = metadataString(config, SHORT_TERM_PROMPT_METADATA);
        if (!StringUtils.hasText(longTermPrompt) && !StringUtils.hasText(shortTermPrompt)) {
            return new AgentCommand(previousMessages);
        }
        List<Message> updated = new ArrayList<>();
        if (StringUtils.hasText(longTermPrompt)) {
            updated.add(new SystemMessage(longTermPrompt));
        }
        if (StringUtils.hasText(shortTermPrompt)) {
            updated.add(new SystemMessage(shortTermPrompt));
        }
        updated.addAll(previousMessages);
        return new AgentCommand(updated, UpdatePolicy.REPLACE);
    }

    private String metadataString(RunnableConfig config, String key) {
        return config == null ? null : config.metadata(key)
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .orElse(null);
    }
}
