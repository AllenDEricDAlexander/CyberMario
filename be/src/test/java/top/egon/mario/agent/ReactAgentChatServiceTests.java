package top.egon.mario.agent;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.agent.dto.request.AgentDebugChatRequest;
import top.egon.mario.agent.model.dto.enums.ModelProviderType;
import top.egon.mario.agent.model.dto.request.ModelOptions;
import top.egon.mario.agent.model.service.model.ModelCallContext;
import top.egon.mario.agent.po.enums.AgentConversationMessageType;
import top.egon.mario.agent.po.enums.AgentConversationRole;
import top.egon.mario.agent.service.AgentConversationAuditService;
import top.egon.mario.agent.service.AgentPresetService;
import top.egon.mario.agent.service.AgentRuntimeFactory;
import top.egon.mario.agent.service.impl.ReactAgentChatService;
import top.egon.mario.agent.service.model.AgentModelConfig;
import top.egon.mario.agent.service.model.AgentOptions;
import top.egon.mario.agent.service.model.AgentRuntimeSpec;
import top.egon.mario.agent.service.model.AgentToolConfig;
import top.egon.mario.agent.tools.arxiv.ArxivToolUserContext;
import top.egon.mario.pojo.response.ChatResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReactAgentChatServiceTests {

    @Test
    void chatUsesThreadIdAndReturnsAgentResponse() throws Exception {
        ReactAgent agent = mock(ReactAgent.class);
        given(agent.stream(eq("你好"), any(RunnableConfig.class)))
                .willAnswer(invocation -> {
                    RunnableConfig config = invocation.getArgument(1);
                    assertThat(config.threadId()).contains("thread-1");
                    return Flux.empty();
                });
        TestSupport support = new TestSupport(agent);

        StepVerifier.create(support.chatService.chat("你好", "thread-1", null))
                .verifyComplete();
    }

    @Test
    void chatCreatesThreadIdWhenRequestDoesNotProvideOne() throws Exception {
        ReactAgent agent = mock(ReactAgent.class);
        given(agent.stream(eq("你好"), any(RunnableConfig.class)))
                .willAnswer(invocation -> {
                    RunnableConfig config = invocation.getArgument(1);
                    assertThat(config.threadId()).isPresent();
                    return Flux.empty();
                });
        TestSupport support = new TestSupport(agent);

        StepVerifier.create(support.chatService.chat("你好", " ", null))
                .verifyComplete();
    }

    @Test
    void chatSetsAndClearsArxivToolUserContext() throws Exception {
        ReactAgent agent = mock(ReactAgent.class);
        RbacPrincipal principal = new RbacPrincipal(8L, "luigi", java.util.Set.of("CHAT_BASIC"), java.util.Set.of(), "v1");
        TestSupport support = new TestSupport(agent);
        given(agent.stream(eq("你好"), any(RunnableConfig.class)))
                .willAnswer(invocation -> {
                    assertThat(support.userContext.get()).isEqualTo(principal);
                    return Flux.empty();
                });

        StepVerifier.create(support.chatService.chat("你好", "thread-1", principal))
                .verifyComplete();

        assertThat(support.userContext.get()).isNull();
    }

    @Test
    void debugChatResolvesSpecAndWritesSuccessfulAudit() throws Exception {
        ReactAgent agent = mock(ReactAgent.class);
        AgentRuntimeSpec debugSpec = runtimeSpec("debug-fingerprint");
        TestSupport support = new TestSupport(agent);
        given(support.presetService.resolveRuntimeSpec(any(AgentDebugChatRequest.class))).willReturn(debugSpec);
        given(support.auditService.start(any(), eq("你好"))).willReturn(99L);
        given(agent.stream(eq("你好"), any(RunnableConfig.class)))
                .willReturn(Flux.just(messageOutput("答案")));

        StepVerifier.create(support.chatService.debugChat(
                        new AgentDebugChatRequest("你好", "thread-1", 9L, null),
                        new RbacPrincipal(8L, "luigi", Set.of("CHAT_BASIC"), Set.of(), "v1")))
                .expectNext(new ChatResponse("thread-1", "答案", "message"))
                .verifyComplete();

        verify(support.runtimeFactory).get(eq(debugSpec), any(ModelCallContext.class));
        verify(support.auditService).start(any(), eq("你好"));
        verify(support.auditService).complete(eq(99L), org.mockito.ArgumentMatchers.argThat(messages ->
                messages.size() == 1
                        && messages.get(0).role() == AgentConversationRole.ASSISTANT
                        && messages.get(0).messageType() == AgentConversationMessageType.MESSAGE
                        && messages.get(0).content().equals("答案")), any(Instant.class));
    }

    @Test
    void debugChatMarksAuditFailedAndReturnsErrorChunkWhenAgentFails() throws Exception {
        ReactAgent agent = mock(ReactAgent.class);
        TestSupport support = new TestSupport(agent);
        given(support.presetService.resolveRuntimeSpec(any(AgentDebugChatRequest.class))).willReturn(runtimeSpec("debug-fingerprint"));
        given(support.auditService.start(any(), eq("你好"))).willReturn(99L);
        given(agent.stream(eq("你好"), any(RunnableConfig.class))).willReturn(Flux.error(new IllegalStateException("boom")));

        StepVerifier.create(support.chatService.debugChat(new AgentDebugChatRequest("你好", "thread-1", 9L, null), null))
                .expectNext(new ChatResponse("thread-1", "模型调用失败：boom", "error"))
                .verifyComplete();

        verify(support.auditService).fail(eq(99L), eq(IllegalStateException.class.getName()), eq("boom"), any(Instant.class));
    }

    @Test
    void chatConvertsAgentFailureToErrorChunk() throws Exception {
        ReactAgent agent = mock(ReactAgent.class);
        TestSupport support = new TestSupport(agent);
        given(support.auditService.start(any(), eq("查这个链接"))).willReturn(99L);
        given(agent.stream(eq("查这个链接"), any(RunnableConfig.class)))
                .willReturn(Flux.error(new IllegalArgumentException("[InvalidParameter] url error, please check url")));

        StepVerifier.create(support.chatService.chat("查这个链接", "thread-1", null))
                .assertNext(response -> {
                    assertThat(response.threadId()).isEqualTo("thread-1");
                    assertThat(response.type()).isEqualTo("error");
                    assertThat(response.message()).contains("模型调用失败");
                    assertThat(response.message()).contains("url error");
                })
                .verifyComplete();

        verify(support.auditService).fail(eq(99L), eq(IllegalArgumentException.class.getName()),
                eq("[InvalidParameter] url error, please check url"), any(Instant.class));
    }

    @Test
    void chatDoesNotEmitUserMessageFromStateFallback() throws Exception {
        ReactAgent agent = mock(ReactAgent.class);
        TestSupport support = new TestSupport(agent);
        given(agent.stream(eq("你好"), any(RunnableConfig.class)))
                .willReturn(Flux.just(userMessageOutput("你好")));

        StepVerifier.create(support.chatService.chat("你好", "thread-1", null))
                .verifyComplete();
    }

    private NodeOutput messageOutput(String text) {
        return NodeOutput.of("node", "agent", new OverAllState(Map.of("messages", java.util.List.of(new AssistantMessage(text)))), null);
    }

    private NodeOutput userMessageOutput(String text) {
        return NodeOutput.of("node", "agent", new OverAllState(Map.of("messages", java.util.List.of(new UserMessage(text)))), null);
    }

    private static AgentRuntimeSpec runtimeSpec(String fingerprint) {
        return new AgentRuntimeSpec(9L,
                new AgentModelConfig(ModelProviderType.DASHSCOPE, "qwen3.6-max-preview"),
                new ModelOptions(new BigDecimal("0.7"), null, new BigDecimal("0.9"), null,
                        true, null, null, true, Map.of()),
                "system prompt",
                new AgentToolConfig(Set.of()),
                new AgentOptions(false, 5, 300),
                fingerprint);
    }

    private static final class TestSupport {

        private final AgentPresetService presetService = mock(AgentPresetService.class);
        private final AgentRuntimeFactory runtimeFactory = mock(AgentRuntimeFactory.class);
        private final AgentConversationAuditService auditService = mock(AgentConversationAuditService.class);
        private final ArxivToolUserContext userContext = new ArxivToolUserContext();
        private final ReactAgentChatService chatService;

        private TestSupport(ReactAgent agent) {
            AgentRuntimeSpec defaultSpec = runtimeSpec("default-fingerprint");
            given(presetService.defaultRuntimeSpec()).willReturn(defaultSpec);
            given(presetService.serializeRuntimeSpec(any())).willReturn("{\"systemPrompt\":\"system prompt\"}");
            given(runtimeFactory.get(eq(defaultSpec), any(ModelCallContext.class))).willReturn(agent);
            given(runtimeFactory.get(org.mockito.ArgumentMatchers.argThat(spec ->
                    spec != null && "debug-fingerprint".equals(spec.fingerprint())), any(ModelCallContext.class))).willReturn(agent);
            given(auditService.start(any(), any())).willReturn(1L);
            doAnswer(invocation -> null).when(auditService).complete(any(), any(), any());
            this.chatService = new ReactAgentChatService(presetService, runtimeFactory, auditService,
                    Schedulers.immediate(), userContext);
        }
    }

}
