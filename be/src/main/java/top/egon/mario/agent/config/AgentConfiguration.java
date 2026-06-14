package top.egon.mario.agent.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import top.egon.mario.agent.hooks.LoggingHook;
import top.egon.mario.agent.interceptor.ToolMonitorInterceptor;
import top.egon.mario.agent.model.api.MarioModelFactory;
import top.egon.mario.agent.model.api.ModelCallContext;
import top.egon.mario.agent.model.api.ModelOptions;
import top.egon.mario.agent.model.api.ModelProviderType;
import top.egon.mario.agent.model.api.ModelRequest;
import top.egon.mario.agent.model.api.ModelScenario;
import top.egon.mario.agent.service.ChatAgentService;
import top.egon.mario.agent.service.impl.ReactAgentChatService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Configures the CyberMario conversation agent and its application service.
 */
@Configuration
@Slf4j
public class AgentConfiguration {

    /**
     * Creates a simple Spring AI Alibaba ReactAgent backed by the model factory.
     */
    @Bean
    public ReactAgent cyberMarioAgent(MarioModelFactory marioModelFactory, List<ToolCallback> toolCallbacks) {
        ModelOptions modelOptions = new ModelOptions(
                BigDecimal.valueOf(0.7),
                null,
                BigDecimal.valueOf(0.9),
                null,
                true,
                null,
                null,
                true,
                Map.of());
        ChatModel chatModel = marioModelFactory.resolve(new ModelRequest(
                        ModelProviderType.DASHSCOPE,
                        "qwen3.6-max-preview",
                        modelOptions,
                        new ModelCallContext(null, null, null, null, ModelScenario.AGENT_CHAT, null, null, null)))
                .chatModel();
        return ReactAgent.builder()
                .name("cyber_mario_agent")
                .model(chatModel)
                .systemPrompt("""
                        You are CyberMario, a concise and helpful conversational assistant.
                        Answer user questions directly and keep the conversation context by thread.
                        """)
                .tools(toolCallbacks.toArray(new ToolCallback[0]))
                .interceptors(new ToolMonitorInterceptor())
                .hooks(new LoggingHook())
                .saver(new MemorySaver())
                .build();
    }

    /**
     * Adapts the blocking agent API to the reactive HTTP layer.
     */
    @Bean
    public ChatAgentService chatAgentService(ReactAgent cyberMarioAgent, Scheduler blockingScheduler) {
        return new ReactAgentChatService(cyberMarioAgent, blockingScheduler);
    }

}
