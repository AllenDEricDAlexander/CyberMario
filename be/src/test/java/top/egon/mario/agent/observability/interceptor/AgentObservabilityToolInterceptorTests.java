package top.egon.mario.agent.observability.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import top.egon.mario.agent.observability.po.enums.AgentRunEventStatus;
import top.egon.mario.agent.observability.po.enums.AgentRunEventType;
import top.egon.mario.agent.observability.po.enums.AgentRunToolType;
import top.egon.mario.agent.observability.service.model.AgentRunAuditContext;
import top.egon.mario.agent.observability.service.model.AgentRunEventRecord;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies tool interceptor records request and response payloads, including MCP identity.
 */
class AgentObservabilityToolInterceptorTests {

    @Test
    void recordsToolRequestAndResponseWithPlaintextPayloads() {
        CapturingRunAuditService auditService = new CapturingRunAuditService();
        AgentObservabilityToolInterceptor interceptor = new AgentObservabilityToolInterceptor(auditService,
                new ObjectMapper());
        AgentRunAuditContext context = new AgentRunAuditContext(7L, "request-1", "trace-1", 8L, "luigi",
                "thread-1", 9L, "fingerprint-1", new AtomicInteger(-1), new AtomicInteger(1),
                Map.of("docs_search", new AgentRunAuditContext.ToolDescriptor(AgentRunToolType.MCP, "docs")));
        ToolCallRequest request = ToolCallRequest.builder()
                .toolName("docs_search")
                .toolCallId("call-1")
                .arguments("{\"query\":\"hello\"}")
                .context(context.metadata())
                .build();

        ToolCallResponse response = interceptor.interceptToolCall(request,
                ignored -> ToolCallResponse.of("call-1", "docs_search", "工具完整返回"));

        assertThat(response.getResult()).isEqualTo("工具完整返回");
        assertThat(auditService.events).hasSize(2);
        AgentRunEventRecord requestEvent = auditService.events.get(0);
        AgentRunEventRecord responseEvent = auditService.events.get(1);
        assertThat(requestEvent.eventType()).isEqualTo(AgentRunEventType.TOOL_REQUEST);
        assertThat(requestEvent.toolArguments()).isEqualTo("{\"query\":\"hello\"}");
        assertThat(requestEvent.toolType()).isEqualTo(AgentRunToolType.MCP);
        assertThat(requestEvent.mcpServerCode()).isEqualTo("docs");
        assertThat(responseEvent.eventType()).isEqualTo(AgentRunEventType.TOOL_RESPONSE);
        assertThat(responseEvent.status()).isEqualTo(AgentRunEventStatus.SUCCESS);
        assertThat(responseEvent.toolResult()).isEqualTo("工具完整返回");
    }

    @Test
    void recordsToolFailureAndReturnsErrorResponse() {
        CapturingRunAuditService auditService = new CapturingRunAuditService();
        AgentObservabilityToolInterceptor interceptor = new AgentObservabilityToolInterceptor(auditService,
                new ObjectMapper());
        AgentRunAuditContext context = new AgentRunAuditContext(7L, "request-1", "trace-1", 8L, "luigi",
                "thread-1", 9L, "fingerprint-1", new AtomicInteger(-1), new AtomicInteger(1), Map.of());
        ToolCallRequest request = ToolCallRequest.builder()
                .toolName("searchWikipedia")
                .toolCallId("call-1")
                .arguments("{\"query\":\"hello\"}")
                .context(context.metadata())
                .build();

        ToolCallResponse response = interceptor.interceptToolCall(request,
                ignored -> {
                    throw new IllegalStateException("boom");
                });

        assertThat(response.isError()).isTrue();
        assertThat(auditService.events).hasSize(2);
        AgentRunEventRecord responseEvent = auditService.events.get(1);
        assertThat(responseEvent.eventType()).isEqualTo(AgentRunEventType.TOOL_RESPONSE);
        assertThat(responseEvent.status()).isEqualTo(AgentRunEventStatus.FAILED);
        assertThat(responseEvent.errorCode()).isEqualTo(IllegalStateException.class.getName());
        assertThat(responseEvent.errorMessage()).isEqualTo("boom");
    }

    @Test
    void toolFailureIsRecordedAsFailedToolResponseWithoutThrowing() {
        CapturingRunAuditService auditService = new CapturingRunAuditService();
        AgentObservabilityToolInterceptor interceptor = new AgentObservabilityToolInterceptor(auditService,
                new ObjectMapper());
        AgentRunAuditContext context = new AgentRunAuditContext(7L, "request-1", "trace-1", 8L, "luigi",
                "thread-1", 9L, "fingerprint-1", new AtomicInteger(-1), new AtomicInteger(1), Map.of());
        ToolCallRequest request = ToolCallRequest.builder()
                .toolName("searchWikipedia")
                .toolCallId("call-1")
                .arguments("{\"query\":\"hello\"}")
                .context(context.metadata())
                .build();

        ToolCallResponse response = interceptor.interceptToolCall(request,
                ignored -> {
                    throw new IllegalArgumentException("tool failed");
                });

        assertThat(response.isError()).isTrue();
        assertThat(auditService.events).extracting(AgentRunEventRecord::eventType)
                .containsExactly(AgentRunEventType.TOOL_REQUEST, AgentRunEventType.TOOL_RESPONSE);
        assertThat(auditService.events.get(1).status()).isEqualTo(AgentRunEventStatus.FAILED);
        assertThat(auditService.events.get(1).errorCode()).isEqualTo(IllegalArgumentException.class.getName());
    }
}
