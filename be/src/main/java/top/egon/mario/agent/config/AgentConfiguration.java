package top.egon.mario.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import top.egon.mario.agent.memory.service.AgentMemoryContextService;
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
import top.egon.mario.agent.tools.arxiv.ArxivToolUserContext;

/**
 * Configures the CyberMario conversation agent and its application service.
 */
@Configuration
public class AgentConfiguration {

    /**
     * Provides the default CyberMario runtime preset used by normal chat and debug presets.
     */
    @Bean
    public AgentRuntimeDefaults agentRuntimeDefaults() {
        return AgentRuntimeDefaults.defaultDefaults();
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
                                             AgentMemoryContextService memoryContextService,
                                             AgentMemoryExtractionService memoryExtractionService) {
        return new ReactAgentChatService(agentPresetService, agentRuntimeFactory, auditService, runAuditService,
                blockingScheduler, arxivToolUserContext, memorySessionService, memoryMessageService,
                memoryContextService, memoryExtractionService);
    }

}
