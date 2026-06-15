package top.egon.mario.agent.observability.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import top.egon.mario.agent.observability.po.enums.AgentRunEventStatus;
import top.egon.mario.agent.observability.po.enums.AgentRunEventType;
import top.egon.mario.agent.observability.service.AgentRunAuditService;
import top.egon.mario.agent.observability.service.model.AgentRunAuditContext;
import top.egon.mario.agent.observability.service.model.AgentRunEventRecord;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies model interceptor records full prompts and model outputs for each ReAct round.
 */
class AgentObservabilityModelInterceptorTests {

    @Test
    void recordsModelRequestAndResponseWithPlaintextPrompt() {
        CapturingRunAuditService auditService = new CapturingRunAuditService();
        AgentObservabilityModelInterceptor interceptor = new AgentObservabilityModelInterceptor(auditService,
                new ObjectMapper());
        AgentRunAuditContext context = context();
        ModelRequest request = ModelRequest.builder()
                .systemMessage(new SystemMessage("system prompt"))
                .messages(List.of(new UserMessage("user prompt")))
                .tools(List.of("searchWikipedia"))
                .toolDescriptions(Map.of("searchWikipedia", "Search Wikipedia"))
                .options(ToolCallingChatOptions.builder().model("qwen-plus").build())
                .context(Map.of(AgentRunAuditContext.METADATA_KEY, context))
                .build();

        ModelResponse response = interceptor.interceptModel(request,
                ignored -> ModelResponse.of(new AssistantMessage("assistant answer")));

        assertThat(response.getMessage()).isInstanceOf(AssistantMessage.class);
        assertThat(auditService.events).hasSize(2);
        AgentRunEventRecord modelRequest = auditService.events.get(0);
        AgentRunEventRecord modelResponse = auditService.events.get(1);
        assertThat(modelRequest.eventType()).isEqualTo(AgentRunEventType.MODEL_REQUEST);
        assertThat(modelRequest.status()).isEqualTo(AgentRunEventStatus.SUCCESS);
        assertThat(modelRequest.reactRound()).isEqualTo(1);
        assertThat(modelRequest.promptText()).contains("system prompt").contains("user prompt");
        assertThat(modelRequest.requestMessagesJson()).contains("user prompt");
        assertThat(modelRequest.availableToolsJson()).contains("searchWikipedia");
        assertThat(modelResponse.eventType()).isEqualTo(AgentRunEventType.MODEL_RESPONSE);
        assertThat(modelResponse.reactRound()).isEqualTo(1);
        assertThat(modelResponse.responseText()).contains("assistant answer");
    }

    @Test
    void skipsWhenAuditContextIsMissing() {
        AgentRunAuditService auditService = mock(AgentRunAuditService.class);
        AgentObservabilityModelInterceptor interceptor = new AgentObservabilityModelInterceptor(auditService,
                new ObjectMapper());
        ModelRequest request = ModelRequest.builder()
                .messages(List.of(new UserMessage("user prompt")))
                .context(Map.of())
                .build();

        ModelResponse response = interceptor.interceptModel(request,
                ignored -> ModelResponse.of(new AssistantMessage("assistant answer")));

        assertThat(response.getMessage()).isInstanceOf(AssistantMessage.class);
    }

    private AgentRunAuditContext context() {
        return new AgentRunAuditContext(7L, "request-1", "trace-1", 8L, "luigi", "thread-1", 9L,
                "fingerprint-1", new AtomicInteger(-1), new AtomicInteger(0), Map.of());
    }
}
