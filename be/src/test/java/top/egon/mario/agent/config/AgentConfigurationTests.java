package top.egon.mario.agent.config;

import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Schedulers;
import top.egon.mario.agent.context.service.AgentContextAssemblyService;
import top.egon.mario.agent.externalim.flow.ChatAgentFlowFactory;
import top.egon.mario.agent.externalim.flow.ChatInvocationPolicy;
import top.egon.mario.agent.externalim.memory.DirectionalAgentMemoryContextService;
import top.egon.mario.agent.externalim.memory.ExternalImMemoryExtractionService;
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
import top.egon.mario.agent.soul.service.AgentSoulService;
import top.egon.mario.agent.tools.arxiv.ArxivToolUserContext;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies agent configuration exposes defaults and reactive chat wiring.
 */
class AgentConfigurationTests {

    @Test
    void agentRuntimeDefaultsKeepCurrentCyberMarioPreset() {
        AgentConfiguration configuration = new AgentConfiguration();

        AgentRuntimeDefaults defaults = configuration.agentRuntimeDefaults();

        assertThat(defaults.modelConfig().model()).isEqualTo("qwen3.6-max-preview");
        assertThat(defaults.modelOptions().temperature()).isEqualByComparingTo(BigDecimal.valueOf(0.7));
        assertThat(defaults.modelOptions().topP()).isEqualByComparingTo(BigDecimal.valueOf(0.9));
        assertThat(defaults.modelOptions().multiModel()).isFalse();
        assertThat(defaults.modelOptions().enableThinking()).isTrue();
        assertThat(defaults.systemPrompt()).contains("CyberMario");
    }

    @Test
    void chatAgentServiceUsesRuntimeFactoryWiring() {
        AgentConfiguration configuration = new AgentConfiguration();

        ChatAgentService service = configuration.chatAgentService(
                mock(AgentPresetService.class),
                mock(AgentRuntimeFactory.class),
                mock(AgentConversationAuditService.class),
                mock(AgentRunAuditService.class),
                Schedulers.immediate(),
                new ArxivToolUserContext(),
                mock(AgentMemorySessionService.class),
                mock(AgentMemoryMessageService.class),
                mock(DirectionalAgentMemoryContextService.class),
                mock(AgentContextAssemblyService.class),
                mock(AgentMemoryExtractionService.class),
                mock(ExternalImMemoryExtractionService.class),
                mock(ChatInvocationPolicy.class),
                mock(ChatAgentFlowFactory.class),
                mock(AgentSoulService.class)
        );

        assertThat(service).isInstanceOf(ReactAgentChatService.class);
    }

}
