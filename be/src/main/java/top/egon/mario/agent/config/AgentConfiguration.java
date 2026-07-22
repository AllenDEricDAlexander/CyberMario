package top.egon.mario.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import top.egon.mario.agent.context.service.AgentContextAssemblyService;
import top.egon.mario.agent.externalim.adapter.qq.QqExternalChatProperties;
import top.egon.mario.agent.externalim.adapter.telegram.TelegramExternalChatProperties;
import top.egon.mario.agent.externalim.flow.ChatAgentFlowFactory;
import top.egon.mario.agent.externalim.flow.ChatInvocationPolicy;
import top.egon.mario.agent.externalim.guard.ChatGuardProperties;
import top.egon.mario.agent.externalim.memory.DirectionalAgentMemoryContextService;
import top.egon.mario.agent.externalim.memory.ExternalImMemoryExtractionService;
import top.egon.mario.agent.externalim.memory.ExternalImMemoryProperties;
import top.egon.mario.agent.externalim.runtime.ExternalChatWorkerProperties;
import top.egon.mario.agent.memory.service.AgentMemoryExtractionService;
import top.egon.mario.agent.memory.service.AgentMemoryMessageService;
import top.egon.mario.agent.memory.service.AgentMemorySessionService;
import top.egon.mario.agent.observability.service.AgentRunAuditService;
import top.egon.mario.agent.service.AgentConversationAuditService;
import top.egon.mario.agent.service.AgentPresetService;
import top.egon.mario.agent.service.AgentRuntimeFactory;
import top.egon.mario.agent.service.ChatAgentService;
import top.egon.mario.agent.service.impl.ReactAgentChatService;
import top.egon.mario.agent.service.model.AgentRuntimeDefaults;
import top.egon.mario.agent.soul.config.AgentSoulProperties;
import top.egon.mario.agent.soul.service.AgentSoulService;
import top.egon.mario.agent.tools.arxiv.ArxivToolUserContext;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Configures the CyberMario conversation agent and its application service.
 */
@Configuration
@Slf4j
@EnableConfigurationProperties({AgentSoulProperties.class, ExternalImMemoryProperties.class,
        ChatGuardProperties.class, ExternalChatWorkerProperties.class,
        TelegramExternalChatProperties.class, QqExternalChatProperties.class})
public class AgentConfiguration {

    /**
     * Provides the default CyberMario runtime preset used by normal chat and debug presets.
     */
    @Bean
    public AgentRuntimeDefaults agentRuntimeDefaults() {
        return AgentRuntimeDefaults.defaultDefaults();
    }

    @Bean(name = "chatGuardExecutor", destroyMethod = "close")
    public ExecutorService chatGuardExecutor() {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("chat-guard-", 0).factory());
    }

    /**
     * Adapts the blocking agent API to the reactive HTTP layer.
     */
    @Bean
    public ChatAgentService chatAgentService(AgentPresetService agentPresetService,
                                             AgentRuntimeFactory agentRuntimeFactory,
                                             AgentConversationAuditService auditService,
                                             AgentRunAuditService runAuditService,
                                             Scheduler blockingScheduler,
                                             ArxivToolUserContext arxivToolUserContext,
                                             AgentMemorySessionService memorySessionService,
                                             AgentMemoryMessageService memoryMessageService,
                                             DirectionalAgentMemoryContextService directionalMemoryContextService,
                                             AgentContextAssemblyService contextAssemblyService,
                                             AgentMemoryExtractionService memoryExtractionService,
                                             ExternalImMemoryExtractionService externalMemoryExtractionService,
                                             ChatInvocationPolicy invocationPolicy,
                                             ChatAgentFlowFactory flowFactory,
                                             AgentSoulService soulService) {
        return new ReactAgentChatService(agentPresetService, agentRuntimeFactory, auditService, runAuditService,
                blockingScheduler, arxivToolUserContext, memorySessionService, memoryMessageService,
                directionalMemoryContextService, contextAssemblyService, memoryExtractionService,
                externalMemoryExtractionService, invocationPolicy, flowFactory, soulService);
    }

}
