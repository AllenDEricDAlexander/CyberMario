package top.egon.mario.agent.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.egon.mario.agent.service.ChatAgentService;
import top.egon.mario.agent.service.impl.ReactAgentChatService;

/**
 * Configures the CyberMario conversation agent and its application service.
 */
@Configuration
public class AgentConfiguration {

    /**
     * Creates a simple Spring AI Alibaba ReactAgent backed by the configured chat model.
     */
    @Bean
    public ReactAgent cyberMarioAgent(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("cyber_mario_agent")
                .model(chatModel)
                .systemPrompt("""
                        You are CyberMario, a concise and helpful conversational assistant.
                        Answer user questions directly and keep the conversation context by thread.
                        """)
                .saver(new MemorySaver())
                .build();
    }

    /**
     * Adapts the blocking agent API to the reactive HTTP layer.
     */
    @Bean
    public ChatAgentService chatAgentService(ReactAgent cyberMarioAgent) {
        return new ReactAgentChatService(cyberMarioAgent);
    }

}
