package top.egon.mario.agent;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.agent.context.service.AgentContextAssemblyService;
import top.egon.mario.agent.context.service.model.AgentContext;
import top.egon.mario.agent.dto.request.AgentDebugChatRequest;
import top.egon.mario.agent.model.dto.enums.ModelProviderType;
import top.egon.mario.agent.model.dto.request.ModelOptions;
import top.egon.mario.agent.model.service.model.ModelCallContext;
import top.egon.mario.agent.memory.hook.AgentMemoryMessagesHook;
import top.egon.mario.agent.memory.po.AgentMemorySessionPo;
import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageRole;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageStatus;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageType;
import top.egon.mario.agent.memory.po.enums.AgentMemorySessionStatus;
import top.egon.mario.agent.memory.service.AgentMemoryContextService;
import top.egon.mario.agent.memory.service.AgentMemoryExtractionService;
import top.egon.mario.agent.memory.service.AgentMemoryMessageService;
import top.egon.mario.agent.memory.service.AgentMemorySessionService;
import top.egon.mario.agent.memory.service.model.AgentMemoryContext;
import top.egon.mario.agent.memory.service.model.AgentMemoryExtractionRequest;
import top.egon.mario.agent.memory.service.model.AgentMemoryMessageRecord;
import top.egon.mario.agent.observability.po.enums.AgentRunToolType;
import top.egon.mario.agent.observability.service.AgentRunAuditService;
import top.egon.mario.agent.observability.service.model.AgentRunAuditContext;
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
import top.egon.mario.agent.soul.po.enums.AgentSoulSourceType;
import top.egon.mario.agent.soul.service.AgentSoulService;
import top.egon.mario.agent.tools.arxiv.ArxivToolUserContext;
import top.egon.mario.pojo.request.ChatRequest;
import top.egon.mario.pojo.response.ChatResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ReactAgentChatServiceTests {

    @Test
    void chatUsesThreadIdAndReturnsAgentResponse() throws Exception {
        ReactAgent agent = mock(ReactAgent.class);
        given(agent.stream(eq("你好"), any(RunnableConfig.class)))
                .willAnswer(invocation -> {
                    RunnableConfig config = invocation.getArgument(1);
                    assertThat(config.threadId()).contains("thread-1");
                    assertThat(config.metadata("agentMemorySessionId")).contains("thread-1");
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
        RbacPrincipal principal = new RbacPrincipal(8L, "luigi", Set.of("CHAT_BASIC"), Set.of(), "v1");
        AgentMemoryContext memoryContext = new AgentMemoryContext("recent prompt", "long prompt");
        given(support.presetService.resolveRuntimeSpec(any(AgentDebugChatRequest.class))).willReturn(debugSpec);
        given(support.auditService.start(any(), eq("你好"))).willReturn(99L);
        given(support.memoryContextService.contextFor(any(), eq(principal), eq(true)))
                .willReturn(memoryContext);
        given(support.contextAssemblyService.assemble(eq(principal), eq(memoryContext), eq(false)))
                .willReturn(new AgentContext("safety prompt", "", "long prompt", "recent prompt"));
        given(agent.stream(eq("你好"), any(RunnableConfig.class)))
                .willAnswer(invocation -> {
                    RunnableConfig config = invocation.getArgument(1);
                    assertThat(config.metadata(AgentMemoryMessagesHook.SAFETY_PROMPT_METADATA_KEY)).contains("safety prompt");
                    assertThat(config.metadata(AgentMemoryMessagesHook.SOUL_PROMPT_METADATA_KEY)).isEmpty();
                    assertThat(config.metadata(AgentMemoryMessagesHook.SHORT_TERM_MEMORY_METADATA_KEY)).contains("recent prompt");
                    assertThat(config.metadata(AgentMemoryMessagesHook.LONG_TERM_MEMORY_METADATA_KEY)).contains("long prompt");
                    return Flux.just(messageOutput("答案"));
                });

        StepVerifier.create(support.chatService.debugChat(
                        new AgentDebugChatRequest("你好", "thread-1", null, null, null, 9L, null),
                        principal))
                .expectNext(new ChatResponse("thread-1", "答案", "message"))
                .verifyComplete();

        verify(support.runtimeFactory).runtime(eq(debugSpec), any(ModelCallContext.class));
        verify(support.runtimeFactory, never()).get(any(), any());
        verify(support.contextAssemblyService).assemble(principal, memoryContext, false);
        verify(support.auditService).start(any(), eq("你好"));
        verify(support.auditService).complete(eq(99L), org.mockito.ArgumentMatchers.argThat(messages ->
                messages.size() == 1
                        && messages.get(0).role() == AgentConversationRole.ASSISTANT
                        && messages.get(0).messageType() == AgentConversationMessageType.MESSAGE
                        && messages.get(0).content().equals("答案")), any(Instant.class));
        verify(support.runAuditService).complete(eq(support.runAuditContext), eq("答案"), eq(null), any(Instant.class));
        InOrder successfulMemoryOrder = inOrder(support.memoryMessageService);
        successfulMemoryOrder.verify(support.memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).role() == AgentMemoryMessageRole.USER
                        && records.get(0).messageType() == AgentMemoryMessageType.MESSAGE
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.SUCCEEDED
                        && records.get(0).content().equals("你好")));
        successfulMemoryOrder.verify(support.memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).role() == AgentMemoryMessageRole.ASSISTANT
                        && records.get(0).messageType() == AgentMemoryMessageType.MESSAGE
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.SUCCEEDED
                        && records.get(0).content().equals("答案")));
        verify(support.memoryExtractionService).extractAfterTurn(any(AgentMemoryExtractionRequest.class));
        verify(support.soulService, never()).maybeEvolveAfterChat(any());
    }

    @Test
    void chatStartsRunAuditAndPassesContextThroughRunnableConfigMetadata() throws Exception {
        ReactAgent agent = mock(ReactAgent.class);
        TestSupport support = new TestSupport(agent);
        RbacPrincipal principal = new RbacPrincipal(8L, "luigi", Set.of("CHAT_BASIC"), Set.of(), "v1");
        AgentMemoryContext memoryContext = new AgentMemoryContext("raw recent", "raw long");
        AgentRunAuditContext context = new AgentRunAuditContext(7L, "request-1", "trace-1",
                8L, "luigi", "thread-1", 9L, "fingerprint-1", new AtomicInteger(-1), new AtomicInteger(0),
                Map.of("docs_search", new AgentRunAuditContext.ToolDescriptor(AgentRunToolType.MCP, "docs")));
        given(support.runtimeFactory.runtime(any(), any(ModelCallContext.class))).willReturn(
                new AgentRuntimeFactory.AgentRuntime(agent, Map.of("docs_search",
                        new AgentRunAuditContext.ToolDescriptor(AgentRunToolType.MCP, "docs"))));
        given(support.runAuditService.start(any())).willReturn(context);
        given(agent.stream(eq("你好"), any(RunnableConfig.class)))
                .willAnswer(invocation -> {
                    RunnableConfig config = invocation.getArgument(1);
                    assertThat(config.metadata(AgentRunAuditContext.METADATA_KEY)).contains(context);
                    assertThat(config.metadata("requestId")).isPresent();
                    assertThat(config.metadata("threadId")).contains("thread-1");
                    assertThat(config.metadata(AgentMemoryMessagesHook.SAFETY_PROMPT_METADATA_KEY)).contains("safety prompt");
                    assertThat(config.metadata(AgentMemoryMessagesHook.SOUL_PROMPT_METADATA_KEY)).contains("soul prompt");
                    assertThat(config.metadata(AgentMemoryMessagesHook.SHORT_TERM_MEMORY_METADATA_KEY)).contains("recent prompt");
                    assertThat(config.metadata(AgentMemoryMessagesHook.LONG_TERM_MEMORY_METADATA_KEY)).contains("long prompt");
                    AgentRunAuditContext metadataContext = (AgentRunAuditContext) config.metadata(AgentRunAuditContext.METADATA_KEY).orElseThrow();
                    assertThat(metadataContext.toolDescriptor("docs_search").toolType()).isEqualTo(AgentRunToolType.MCP);
                    return Flux.just(messageOutput("答案"));
                });

        given(support.memoryContextService.contextFor(any(), any(), eq(true)))
                .willReturn(memoryContext);
        given(support.contextAssemblyService.assemble(eq(principal), eq(memoryContext), eq(true)))
                .willReturn(new AgentContext("safety prompt", "soul prompt", "long prompt", "recent prompt"));

        StepVerifier.create(support.chatService.chat("你好", "thread-1", principal))
                .expectNext(new ChatResponse("thread-1", "答案", "message"))
                .verifyComplete();

        verify(support.runAuditService).start(org.mockito.ArgumentMatchers.argThat(start ->
                start.userId().equals(8L)
                        && start.username().equals("luigi")
                        && start.threadId().equals("thread-1")
                        && start.userMessage().equals("你好")
                        && start.effectiveConfigJson().contains("systemPrompt")));
        verify(support.contextAssemblyService).assemble(principal, memoryContext, true);
        verify(support.runAuditService).complete(eq(context), eq("答案"), eq(null), any(Instant.class));
    }

    @Test
    void chatEvolvesSoulAfterSuccessfulNormalChat() throws Exception {
        ReactAgent agent = mock(ReactAgent.class);
        TestSupport support = new TestSupport(agent);
        RbacPrincipal principal = new RbacPrincipal(8L, "luigi", Set.of("CHAT_BASIC"), Set.of(), "v1");
        AgentMemoryContext memoryContext = new AgentMemoryContext("raw recent", "raw long");
        given(support.memoryContextService.contextFor(any(), eq(principal), eq(true)))
                .willReturn(memoryContext);
        given(support.contextAssemblyService.assemble(eq(principal), eq(memoryContext), eq(true)))
                .willReturn(new AgentContext("safety prompt", "soul prompt", "long prompt", "recent prompt"));
        given(agent.stream(eq("你好"), any(RunnableConfig.class)))
                .willReturn(Flux.just(messageOutput("答案")));

        StepVerifier.create(support.chatService.chat("你好", "thread-1", principal))
                .expectNext(new ChatResponse("thread-1", "答案", "message"))
                .verifyComplete();

        verify(support.soulService).maybeEvolveAfterChat(org.mockito.ArgumentMatchers.argThat(request ->
                request.principal().equals(principal)
                        && request.sessionId().equals("thread-1")
                        && request.userMessage().equals("你好")
                        && request.assistantMessage().equals("答案")
                        && request.recentContextPrompt().equals("recent prompt")
                        && request.sourceType() == AgentSoulSourceType.AGENT_CHAT
                        && request.requestId() != null
                        && !request.requestId().isBlank()));
    }

    @Test
    void debugChatDoesNotEvolveSoulWhenResolvedSessionWasNormalChat() throws Exception {
        ReactAgent agent = mock(ReactAgent.class);
        AgentRuntimeSpec debugSpec = runtimeSpec("debug-fingerprint");
        TestSupport support = new TestSupport(agent);
        RbacPrincipal principal = new RbacPrincipal(8L, "luigi", Set.of("CHAT_BASIC"), Set.of(), "v1");
        given(support.presetService.resolveRuntimeSpec(any(AgentDebugChatRequest.class))).willReturn(debugSpec);
        given(support.memorySessionService.resolveOrCreate(
                eq(AgentMemoryEntryType.AGENT_DEBUG), eq("thread-1"), isNull(), isNull(), eq(principal)))
                .willReturn(session("thread-1", AgentMemoryEntryType.AGENT_CHAT));
        given(agent.stream(eq("你好"), any(RunnableConfig.class)))
                .willReturn(Flux.just(messageOutput("答案")));

        StepVerifier.create(support.chatService.debugChat(
                        new AgentDebugChatRequest("你好", "thread-1", null, null, null, 9L, null),
                        principal))
                .expectNext(new ChatResponse("thread-1", "答案", "message"))
                .verifyComplete();

        verify(support.soulService, never()).maybeEvolveAfterChat(any());
    }

    @Test
    void debugChatMarksAuditFailedAndReturnsErrorChunkWhenAgentFails() throws Exception {
        ReactAgent agent = mock(ReactAgent.class);
        TestSupport support = new TestSupport(agent);
        given(support.presetService.resolveRuntimeSpec(any(AgentDebugChatRequest.class))).willReturn(runtimeSpec("debug-fingerprint"));
        given(support.auditService.start(any(), eq("你好"))).willReturn(99L);
        given(agent.stream(eq("你好"), any(RunnableConfig.class))).willReturn(Flux.error(new IllegalStateException("boom")));

        StepVerifier.create(support.chatService.debugChat(
                        new AgentDebugChatRequest("你好", "thread-1", null, null, null, 9L, null), null))
                .expectNext(new ChatResponse("thread-1", "模型调用失败：boom", "error"))
                .verifyComplete();

        verify(support.auditService).fail(eq(99L), eq(IllegalStateException.class.getName()), eq("boom"), any(Instant.class));
        verify(support.runAuditService).fail(eq(support.runAuditContext), eq(IllegalStateException.class.getName()),
                eq("boom"), any(Instant.class));
        verify(support.soulService, never()).maybeEvolveAfterChat(any());
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
        verify(support.runAuditService).fail(eq(support.runAuditContext), eq(IllegalArgumentException.class.getName()),
                eq("[InvalidParameter] url error, please check url"), any(Instant.class));
        verify(support.soulService, never()).maybeEvolveAfterChat(any());
    }

    @Test
    void chatConvertsFrameworkExceptionMessageToErrorChunk() throws Exception {
        ReactAgent agent = mock(ReactAgent.class);
        TestSupport support = new TestSupport(agent);
        String frameworkError = "Exception: 400 - {\"code\":\"InvalidParameter\",\"message\":\"url error, please check url\"}";
        given(agent.stream(eq("查这个链接"), any(RunnableConfig.class)))
                .willReturn(Flux.just(messageOutput(frameworkError)));

        StepVerifier.create(support.chatService.chat("查这个链接", "thread-1", null))
                .assertNext(response -> {
                    assertThat(response.threadId()).isEqualTo("thread-1");
                    assertThat(response.type()).isEqualTo("error");
                    assertThat(response.message()).contains("模型调用失败");
                    assertThat(response.message()).contains("url error");
                    assertThat(response.message()).doesNotContain("Exception:");
                })
                .verifyComplete();

        verify(support.auditService).fail(eq(1L), org.mockito.ArgumentMatchers.anyString(),
                eq("400 - {\"code\":\"InvalidParameter\",\"message\":\"url error, please check url\"}"), any(Instant.class));
        verify(support.runAuditService).fail(eq(support.runAuditContext), org.mockito.ArgumentMatchers.anyString(),
                eq("400 - {\"code\":\"InvalidParameter\",\"message\":\"url error, please check url\"}"), any(Instant.class));
        InOrder inOrder = inOrder(support.memoryMessageService);
        inOrder.verify(support.memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).role() == AgentMemoryMessageRole.USER
                        && records.get(0).content().equals("查这个链接")));
        inOrder.verify(support.memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).role() == AgentMemoryMessageRole.ASSISTANT
                        && records.get(0).messageType() == AgentMemoryMessageType.ERROR
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.FAILED
                        && records.get(0).content().contains("模型调用失败")
                        && records.get(0).content().contains("url error")
                        && !records.get(0).content().contains("Exception:")));
        verify(support.memoryExtractionService, never()).extractAfterTurn(any());
        verify(support.soulService, never()).maybeEvolveAfterChat(any());
    }

    @Test
    void chatPersistsOnlyFinalCumulativeAssistantMessage() throws Exception {
        ReactAgent agent = mock(ReactAgent.class);
        TestSupport support = new TestSupport(agent);
        given(agent.stream(eq("你好"), any(RunnableConfig.class)))
                .willReturn(Flux.just(messageOutput("你"), messageOutput("你好"), messageOutput("你好，Mario")));

        StepVerifier.create(support.chatService.chat("你好", "thread-1", null))
                .expectNext(new ChatResponse("thread-1", "你", "message"))
                .expectNext(new ChatResponse("thread-1", "你好", "message"))
                .expectNext(new ChatResponse("thread-1", "你好，Mario", "message"))
                .verifyComplete();

        InOrder inOrder = inOrder(support.memoryMessageService);
        inOrder.verify(support.memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).role() == AgentMemoryMessageRole.USER
                        && records.get(0).content().equals("你好")));
        inOrder.verify(support.memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).role() == AgentMemoryMessageRole.ASSISTANT
                        && records.get(0).messageType() == AgentMemoryMessageType.MESSAGE
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.SUCCEEDED
                        && records.get(0).content().equals("你好，Mario")));
    }

    @Test
    void chatSuppressesFinalStateSnapshotAlreadySentByStreamingChunks() throws Exception {
        ReactAgent agent = mock(ReactAgent.class);
        TestSupport support = new TestSupport(agent);
        given(agent.stream(eq("你好"), any(RunnableConfig.class)))
                .willReturn(Flux.just(
                        directOutput(new AssistantMessage("你"), "AGENT_MODEL_STREAMING"),
                        directOutput(new AssistantMessage("好"), "AGENT_MODEL_STREAMING"),
                        messageOutput("你好")));

        StepVerifier.create(support.chatService.chat("你好", "thread-1", null))
                .expectNext(new ChatResponse("thread-1", "你", "message"))
                .expectNext(new ChatResponse("thread-1", "好", "message"))
                .verifyComplete();

        verify(support.auditService).complete(eq(1L), org.mockito.ArgumentMatchers.argThat(messages ->
                messages.size() == 1
                        && messages.get(0).messageType() == AgentConversationMessageType.MESSAGE
                        && messages.get(0).content().equals("你好")), any(Instant.class));
        verify(support.runAuditService).complete(eq(support.runAuditContext), eq("你好"), eq(null), any(Instant.class));
        InOrder inOrder = inOrder(support.memoryMessageService);
        inOrder.verify(support.memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).role() == AgentMemoryMessageRole.USER
                        && records.get(0).content().equals("你好")));
        inOrder.verify(support.memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).role() == AgentMemoryMessageRole.ASSISTANT
                        && records.get(0).messageType() == AgentMemoryMessageType.MESSAGE
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.SUCCEEDED
                        && records.get(0).content().equals("你好")));
    }

    @Test
    void chatDeduplicatesRepeatedStateMessageSnapshots() throws Exception {
        ReactAgent agent = mock(ReactAgent.class);
        TestSupport support = new TestSupport(agent);
        String answer = "Hello! I'm CyberMario. How can I help you today?";
        given(agent.stream(eq("hello"), any(RunnableConfig.class)))
                .willReturn(Flux.just(
                        messageOutput(answer),
                        messageOutput(answer),
                        messageOutput(answer),
                        messageOutput(answer)));

        StepVerifier.create(support.chatService.chat("hello", "thread-1", null))
                .expectNext(new ChatResponse("thread-1", answer, "message"))
                .verifyComplete();

        verify(support.auditService).complete(eq(1L), org.mockito.ArgumentMatchers.argThat(messages ->
                messages.size() == 1
                        && messages.get(0).role() == AgentConversationRole.ASSISTANT
                        && messages.get(0).messageType() == AgentConversationMessageType.MESSAGE
                        && messages.get(0).content().equals(answer)), any(Instant.class));
        verify(support.runAuditService).complete(eq(support.runAuditContext), eq(answer), eq(null), any(Instant.class));
        InOrder inOrder = inOrder(support.memoryMessageService);
        inOrder.verify(support.memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).role() == AgentMemoryMessageRole.USER
                        && records.get(0).content().equals("hello")));
        inOrder.verify(support.memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).role() == AgentMemoryMessageRole.ASSISTANT
                        && records.get(0).messageType() == AgentMemoryMessageType.MESSAGE
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.SUCCEEDED
                        && records.get(0).content().equals(answer)));
    }

    @Test
    void chatPersistsOnlyFinalCumulativeThinkingMessage() throws Exception {
        ReactAgent agent = mock(ReactAgent.class);
        TestSupport support = new TestSupport(agent);
        given(agent.stream(eq("分析一下"), any(RunnableConfig.class)))
                .willReturn(Flux.just(
                        directOutput(new AssistantMessage("分析"), "THINKING"),
                        directOutput(new AssistantMessage("分析问题"), "THINKING"),
                        messageOutput("最终回答")));

        StepVerifier.create(support.chatService.chat("分析一下", "thread-1", null))
                .expectNext(new ChatResponse("thread-1", "分析", "think"))
                .expectNext(new ChatResponse("thread-1", "分析问题", "think"))
                .expectNext(new ChatResponse("thread-1", "最终回答", "message"))
                .verifyComplete();

        verify(support.memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 2
                        && records.get(0).messageType() == AgentMemoryMessageType.THINK
                        && records.get(0).content().equals("分析问题")
                        && records.get(1).messageType() == AgentMemoryMessageType.MESSAGE
                        && records.get(1).content().equals("最终回答")));
    }

    @Test
    void chatPersistsUserAndAssistantErrorWhenAgentFails() throws Exception {
        ReactAgent agent = mock(ReactAgent.class);
        TestSupport support = new TestSupport(agent);
        IllegalArgumentException failure = new IllegalArgumentException("[InvalidParameter] url error");
        given(agent.stream(eq("查这个链接"), any(RunnableConfig.class))).willReturn(Flux.error(failure));

        StepVerifier.create(support.chatService.chat("查这个链接", "thread-1", null))
                .assertNext(response -> {
                    assertThat(response.threadId()).isEqualTo("thread-1");
                    assertThat(response.type()).isEqualTo("error");
                    assertThat(response.message()).contains("模型调用失败");
                    assertThat(response.message()).contains("url error");
                })
                .verifyComplete();

        InOrder inOrder = inOrder(support.memoryMessageService);
        inOrder.verify(support.memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).role() == AgentMemoryMessageRole.USER
                        && records.get(0).messageType() == AgentMemoryMessageType.MESSAGE
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.SUCCEEDED
                        && records.get(0).content().equals("查这个链接")));
        inOrder.verify(support.memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).role() == AgentMemoryMessageRole.ASSISTANT
                        && records.get(0).messageType() == AgentMemoryMessageType.ERROR
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.FAILED
                        && records.get(0).content().contains("模型调用失败")
                        && records.get(0).errorCode().equals(IllegalArgumentException.class.getName())
                        && records.get(0).errorMessage().equals("[InvalidParameter] url error")));
        verify(support.memoryExtractionService, never()).extractAfterTurn(any());
    }

    @Test
    void chatTreatsSessionIdAsConversationThreadAndKeepsThreadIdCompatibility() throws Exception {
        ReactAgent agent = mock(ReactAgent.class);
        TestSupport support = new TestSupport(agent);
        given(support.memorySessionService.resolveOrCreate(
                eq(AgentMemoryEntryType.AGENT_CHAT), eq("session-1"), eq(false), eq(true), any()))
                .willReturn(session("session-1", AgentMemoryEntryType.AGENT_CHAT));
        given(agent.stream(eq("你好"), any(RunnableConfig.class)))
                .willAnswer(invocation -> {
                    RunnableConfig config = invocation.getArgument(1);
                    assertThat(config.threadId()).contains("session-1");
                    assertThat(config.metadata("agentMemoryEntryType")).contains("AGENT_CHAT");
                    return Flux.just(messageOutput("答案"));
                });

        StepVerifier.create(support.chatService.chat(
                        new ChatRequest("你好", "legacy-thread", "session-1", false),
                        new RbacPrincipal(8L, "luigi", Set.of("CHAT_BASIC"), Set.of(), "v1")))
                .expectNext(new ChatResponse("session-1", "答案", "message"))
                .verifyComplete();
    }

    @Test
    void chatPreservesExistingMemoryContextSwitchWhenRequestOmitsFlag() throws Exception {
        ReactAgent agent = mock(ReactAgent.class);
        TestSupport support = new TestSupport(agent);
        RbacPrincipal principal = new RbacPrincipal(8L, "luigi", Set.of("CHAT_BASIC"), Set.of(), "v1");
        AgentMemorySessionPo disabledSession = session("thread-1", AgentMemoryEntryType.AGENT_CHAT);
        disabledSession.setMemoryEnabled(false);
        given(support.memorySessionService.resolveOrCreate(
                eq(AgentMemoryEntryType.AGENT_CHAT), eq("thread-1"), isNull(), eq(true), eq(principal)))
                .willReturn(disabledSession);
        given(agent.stream(eq("你好"), any(RunnableConfig.class))).willReturn(Flux.empty());

        StepVerifier.create(support.chatService.chat(new ChatRequest("你好", "thread-1", null, null), principal))
                .verifyComplete();

        verify(support.memorySessionService).resolveOrCreate(
                eq(AgentMemoryEntryType.AGENT_CHAT), eq("thread-1"), isNull(), eq(true), eq(principal));
        verify(support.memoryContextService).contextFor(disabledSession, principal, false);
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

    private NodeOutput directOutput(Message message, String outputType) {
        return new DirectMessageOutput(message, outputType);
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
        private final AgentRunAuditService runAuditService = mock(AgentRunAuditService.class);
        private final AgentMemorySessionService memorySessionService = mock(AgentMemorySessionService.class);
        private final AgentMemoryMessageService memoryMessageService = mock(AgentMemoryMessageService.class);
        private final AgentMemoryContextService memoryContextService = mock(AgentMemoryContextService.class);
        private final AgentContextAssemblyService contextAssemblyService = mock(AgentContextAssemblyService.class);
        private final AgentMemoryExtractionService memoryExtractionService = mock(AgentMemoryExtractionService.class);
        private final AgentSoulService soulService = mock(AgentSoulService.class);
        private final ArxivToolUserContext userContext = new ArxivToolUserContext();
        private final AgentRunAuditContext runAuditContext = new AgentRunAuditContext(7L, "request-1", "trace-1",
                8L, "luigi", "thread-1", 9L, "fingerprint-1", new AtomicInteger(-1), new AtomicInteger(0),
                Map.of());
        private final ReactAgentChatService chatService;

        private TestSupport(ReactAgent agent) {
            AgentRuntimeSpec defaultSpec = runtimeSpec("default-fingerprint");
            given(presetService.defaultRuntimeSpec()).willReturn(defaultSpec);
            given(presetService.serializeRuntimeSpec(any())).willReturn("{\"systemPrompt\":\"system prompt\"}");
            given(runtimeFactory.runtime(eq(defaultSpec), any(ModelCallContext.class)))
                    .willReturn(new AgentRuntimeFactory.AgentRuntime(agent, Map.of()));
            given(runtimeFactory.runtime(org.mockito.ArgumentMatchers.argThat(spec ->
                    spec != null && "debug-fingerprint".equals(spec.fingerprint())), any(ModelCallContext.class)))
                    .willReturn(new AgentRuntimeFactory.AgentRuntime(agent, Map.of()));
            given(auditService.start(any(), any())).willReturn(1L);
            given(runAuditService.start(any())).willReturn(runAuditContext);
            given(memorySessionService.resolveOrCreate(any(), any(), any(), any(), any()))
                    .willAnswer(invocation -> {
                        AgentMemoryEntryType entryType = invocation.getArgument(0);
                        String sessionId = invocation.getArgument(1);
                        return session(sessionId == null || sessionId.isBlank() ? "thread-1" : sessionId, entryType);
                    });
            given(memoryContextService.contextFor(any(), any(), any(Boolean.class)))
                    .willReturn(new AgentMemoryContext("", ""));
            given(contextAssemblyService.assemble(any(), any(), anyBoolean()))
                    .willReturn((AgentContext) null);
            given(memoryMessageService.nextTurnNo(any())).willReturn(1);
            doAnswer(invocation -> null).when(auditService).complete(any(), any(), any());
            this.chatService = new ReactAgentChatService(presetService, runtimeFactory, auditService, runAuditService,
                    Schedulers.immediate(), userContext, memorySessionService, memoryMessageService,
                    memoryContextService, contextAssemblyService, memoryExtractionService, soulService);
        }
    }

    private static final class DirectMessageOutput extends NodeOutput {

        private final Message message;

        private final String outputType;

        private DirectMessageOutput(Message message, String outputType) {
            super("node", "agent", new OverAllState(Map.of()));
            this.message = message;
            this.outputType = outputType;
        }

        public Message message() {
            return message;
        }

        public String getOutputType() {
            return outputType;
        }
    }

    private static AgentMemorySessionPo session(String sessionId, AgentMemoryEntryType entryType) {
        AgentMemorySessionPo session = new AgentMemorySessionPo();
        session.setSessionId(sessionId);
        session.setEntryType(entryType);
        session.setUserId(8L);
        session.setUsername("luigi");
        session.setStatus(AgentMemorySessionStatus.ACTIVE);
        session.setMemoryEnabled(true);
        session.setLongTermExtractionEnabled(true);
        session.setShortTermWindowTurns(10);
        return session;
    }

}
