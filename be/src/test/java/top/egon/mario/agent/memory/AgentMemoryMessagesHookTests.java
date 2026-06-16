package top.egon.mario.agent.memory;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import top.egon.mario.agent.memory.hook.AgentMemoryMessagesHook;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies memory prompts are injected as system messages before model calls.
 */
class AgentMemoryMessagesHookTests {

    @Test
    void injectsLongAndShortTermPromptsAheadOfCurrentMessages() {
        AgentMemoryMessagesHook hook = new AgentMemoryMessagesHook();
        RunnableConfig config = RunnableConfig.builder()
                .addMetadata("agentMemoryLongTermPrompt", "长期记忆")
                .addMetadata("agentMemoryShortTermPrompt", "短期记忆")
                .build();

        var command = hook.beforeModel(List.of(new UserMessage("你好")), config);

        assertThat(commandMessages(command)).hasSize(3);
        assertThat(commandMessages(command).get(0)).isInstanceOf(SystemMessage.class);
        assertThat(commandMessages(command).get(0).getText()).contains("长期记忆");
        assertThat(commandMessages(command).get(1).getText()).contains("短期记忆");
        assertThat(commandMessages(command).get(2).getText()).isEqualTo("你好");
    }

    @Test
    void leavesMessagesUnchangedWhenNoMemoryPromptExists() {
        AgentMemoryMessagesHook hook = new AgentMemoryMessagesHook();
        List<Message> messages = List.of(new UserMessage("你好"));

        var command = hook.beforeModel(messages, RunnableConfig.builder().build());

        assertThat(commandMessages(command)).isSameAs(messages);
    }

    @SuppressWarnings("unchecked")
    private List<Message> commandMessages(Object command) {
        try {
            var method = command.getClass().getDeclaredMethod("getMessages");
            method.setAccessible(true);
            return (List<Message>) method.invoke(command);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
