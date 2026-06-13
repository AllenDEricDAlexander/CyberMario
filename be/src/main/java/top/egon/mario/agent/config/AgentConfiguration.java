package top.egon.mario.agent.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.egon.mario.agent.hooks.LoggingHook;
import top.egon.mario.agent.interceptor.ToolMonitorInterceptor;
import top.egon.mario.agent.service.ChatAgentService;
import top.egon.mario.agent.service.impl.ReactAgentChatService;

import java.util.List;

/**
 * Configures the CyberMario conversation agent and its application service.
 */
@Configuration
public class AgentConfiguration {

    /**
     * Creates a simple Spring AI Alibaba ReactAgent backed by the configured chat model.
     */
    @Bean
    public ReactAgent cyberMarioAgent(DashScopeApi dashScopeApi, List<ToolCallback> toolCallbacks) {
        ChatModel chatModel = DashScopeChatModel.builder()
                .defaultOptions(DashScopeChatOptions.builder()
                        .model("qwen3.6-max-preview")
                        .temperature(0.7)
                        .multiModel(true)
                        .enableThinking(true)
                        .topP(0.9)
                        .build())
                .dashScopeApi(dashScopeApi)
                .build();
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

    @Bean
    public DashScopeApi dashScopeApi(@Value("${spring.ai.dashscope.api-key:${AI_DASHSCOPE_API_KEY:}}") String apiKey) {
        return DashScopeApi.builder().apiKey(apiKey).build();
    }

    /**
     * Adapts the blocking agent API to the reactive HTTP layer.
     */
    @Bean
    public ChatAgentService chatAgentService(ReactAgent cyberMarioAgent) {
        return new ReactAgentChatService(cyberMarioAgent);
    }

}
