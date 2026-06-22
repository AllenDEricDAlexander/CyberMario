package top.egon.mario.agent.service.impl;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Scheduler;
import top.egon.mario.agent.context.service.AgentContextAssemblyService;
import top.egon.mario.agent.context.service.model.AgentContext;
import top.egon.mario.agent.dto.request.AgentDebugChatRequest;
import top.egon.mario.agent.memory.po.AgentMemorySessionPo;
import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageRole;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageStatus;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageType;
import top.egon.mario.agent.memory.service.AgentMemoryContextService;
import top.egon.mario.agent.memory.service.AgentMemoryExtractionService;
import top.egon.mario.agent.memory.service.AgentMemoryMessageService;
import top.egon.mario.agent.memory.service.AgentMemorySessionService;
import top.egon.mario.agent.memory.service.model.AgentMemoryContext;
import top.egon.mario.agent.memory.service.model.AgentMemoryExtractionRequest;
import top.egon.mario.agent.memory.service.model.AgentMemoryMessageRecord;
import top.egon.mario.agent.memory.service.model.AgentMemoryTextAccumulator;
import top.egon.mario.agent.model.dto.enums.ModelScenario;
import top.egon.mario.agent.model.service.model.ModelCallContext;
import top.egon.mario.agent.observability.service.AgentRunAuditService;
import top.egon.mario.agent.observability.service.model.AgentRunAuditContext;
import top.egon.mario.agent.observability.service.model.AgentRunAuditStart;
import top.egon.mario.agent.po.enums.AgentConversationMessageType;
import top.egon.mario.agent.po.enums.AgentConversationRole;
import top.egon.mario.agent.service.AgentConversationAuditService;
import top.egon.mario.agent.service.AgentPresetService;
import top.egon.mario.agent.service.AgentRuntimeFactory;
import top.egon.mario.agent.service.ChatAgentService;
import top.egon.mario.agent.service.model.AgentConversationAuditStart;
import top.egon.mario.agent.service.model.AgentConversationMessageRecord;
import top.egon.mario.agent.service.model.AgentRuntimeSpec;
import top.egon.mario.agent.tools.arxiv.ArxivToolUserContext;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.pojo.request.ChatRequest;
import top.egon.mario.pojo.response.ChatResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reactive adapter around Spring AI Alibaba ReactAgent's streaming API.
 * <p>
 * Uses {@link ReactAgent#stream(String, RunnableConfig)} for token-level
 * streaming and exposes both reasoning (think) and final message chunks.
 */
@Slf4j
@Validated
public class ReactAgentChatService implements ChatAgentService {

    private final AgentPresetService agentPresetService;
    private final AgentRuntimeFactory agentRuntimeFactory;
    private final AgentConversationAuditService auditService;
    private final AgentRunAuditService runAuditService;
    private final Scheduler blockingScheduler;
    private final ArxivToolUserContext arxivToolUserContext;
    private final AgentMemorySessionService memorySessionService;
    private final AgentMemoryMessageService memoryMessageService;
    private final AgentMemoryContextService memoryContextService;
    private final AgentContextAssemblyService contextAssemblyService;
    private final AgentMemoryExtractionService memoryExtractionService;

    public ReactAgentChatService(AgentPresetService agentPresetService, AgentRuntimeFactory agentRuntimeFactory,
                                 AgentConversationAuditService auditService, AgentRunAuditService runAuditService,
                                 Scheduler blockingScheduler, ArxivToolUserContext arxivToolUserContext,
                                 AgentMemorySessionService memorySessionService,
                                 AgentMemoryMessageService memoryMessageService,
                                 AgentMemoryContextService memoryContextService,
                                 AgentContextAssemblyService contextAssemblyService,
                                 AgentMemoryExtractionService memoryExtractionService) {
        this.agentPresetService = agentPresetService;
        this.agentRuntimeFactory = agentRuntimeFactory;
        this.auditService = auditService;
        this.runAuditService = runAuditService;
        this.blockingScheduler = blockingScheduler;
        this.arxivToolUserContext = arxivToolUserContext;
        this.memorySessionService = memorySessionService;
        this.memoryMessageService = memoryMessageService;
        this.memoryContextService = memoryContextService;
        this.contextAssemblyService = contextAssemblyService;
        this.memoryExtractionService = memoryExtractionService;
    }

    @Override
    public Flux<ChatResponse> chat(ChatRequest request, RbacPrincipal principal) {
        return executeChat(request, principal, null);
    }

    @Override
    public Flux<ChatResponse> debugChat(AgentDebugChatRequest request, RbacPrincipal principal) {
        return executeChat(new ChatRequest(request.message(), request.threadId(), request.sessionId(), request.memoryEnabled()),
                principal, request);
    }

    private Flux<ChatResponse> executeChat(ChatRequest request, RbacPrincipal principal, AgentDebugChatRequest debugRequest) {
        return Flux.deferContextual(contextView -> {
            String traceId = TraceContext.traceId(contextView);
            String requestId = UUID.randomUUID().toString();
            String message = request.message();
            AgentMemoryEntryType entryType = debugRequest == null
                    ? AgentMemoryEntryType.AGENT_CHAT
                    : AgentMemoryEntryType.AGENT_DEBUG;
            AgentMemorySessionPo memorySession = memorySessionService.resolveOrCreate(
                    entryType,
                    resolveSessionId(request.sessionId(), request.threadId()),
                    request.memoryContextEnabled(),
                    debugRequest == null ? Boolean.TRUE : debugRequest.longTermExtractionEnabled(),
                    principal);
            boolean memoryContextEnabled = memorySession.isMemoryEnabled();
            String conversationThreadId = memorySession.getSessionId();
            AgentMemoryContext memoryContext = memoryContextService.contextFor(
                    memorySession, principal, memoryContextEnabled);
            AgentContext agentContext = contextAssemblyService.assemble(principal, memoryContext, debugRequest == null);
            AgentRuntimeSpec spec = debugRequest == null
                    ? agentPresetService.defaultRuntimeSpec()
                    : agentPresetService.resolveRuntimeSpec(debugRequest);
            ModelCallContext modelCallContext = new ModelCallContext(
                    principal == null ? null : principal.userId(),
                    traceId,
                    null,
                    conversationThreadId,
                    ModelScenario.AGENT_CHAT,
                    requestId,
                    null,
                    null
            );
            AgentRuntimeFactory.AgentRuntime runtime = agentRuntimeFactory.runtime(spec, modelCallContext);
            AtomicReference<Long> auditId = new AtomicReference<>();
            AtomicReference<AgentRunAuditContext> runAuditContext = new AtomicReference<>();
            int turnNo = memoryMessageService.nextTurnNo(memorySession.getSessionId());
            persistUserMemory(memorySession, message, turnNo, requestId, traceId);
            AgentMemoryTextAccumulator messageContent = new AgentMemoryTextAccumulator();
            AgentMemoryTextAccumulator thinkContent = new AgentMemoryTextAccumulator();
            AtomicReference<String> lastStateSnapshotKey = new AtomicReference<>();
            TraceContext.withMdc(traceId, () -> LogUtil.info(log).log("agent chat started, threadId={}, messageLength={}",
                    conversationThreadId, message == null ? 0 : message.length()));
            return Mono.just(RunnableConfig.builder().threadId(conversationThreadId).build())
                    .flatMapMany(cfg -> {
                        try {
                            auditId.set(startAudit(requestId, traceId, principal, conversationThreadId, spec, message));
                            AgentRunAuditContext context = startRunAudit(requestId, traceId, principal,
                                    conversationThreadId, spec, message, runtime.toolDescriptors());
                            runAuditContext.set(context);
                            arxivToolUserContext.set(principal);
                            return runtime.agent().stream(message, runnableConfig(cfg, context, agentContext,
                                    memorySession, entryType));
                        } catch (GraphRunnerException e) {
                            return Flux.error(e);
                        }
                    })
                    .doFinally(signalType -> arxivToolUserContext.clear())
                    .doOnComplete(() -> TraceContext.withMdc(traceId,
                            () -> LogUtil.info(log).log("agent chat completed, threadId={}", conversationThreadId)))
                    .doOnError(error -> TraceContext.withMdc(traceId,
                            () -> LogUtil.error(log).log("agent chat failed, threadId={}", conversationThreadId, error)))
                    .subscribeOn(blockingScheduler)
                    .flatMap(output -> toChatChunk(output, conversationThreadId))
                    .filter(chunk -> shouldEmitChunk(chunk, lastStateSnapshotKey))
                    .doOnNext(chunk -> collectAuditChunk(chunk, messageContent, thinkContent))
                    .map(AgentChatChunk::response)
                    .doFinally(signalType -> {
                        finishAudit(signalType, auditId.get(), messageContent, thinkContent, null);
                        finishRunAudit(signalType, runAuditContext.get(), messageContent, thinkContent, null);
                        finishAssistantMemory(signalType, memorySession, turnNo, messageContent, thinkContent,
                                requestId, traceId);
                    })
                    .onErrorResume(error -> {
                        String userFacingError = errorMessage(error);
                        failAudit(auditId.get(), error);
                        failRunAudit(runAuditContext.get(), error);
                        failAssistantMemory(memorySession, turnNo, userFacingError, error, requestId, traceId);
                        return Flux.just(new ChatResponse(conversationThreadId, userFacingError, "error"));
                    });
        });
    }

    private Long startAudit(String requestId, String traceId, RbacPrincipal principal, String threadId,
                            AgentRuntimeSpec spec, String message) {
        return auditService.start(new AgentConversationAuditStart(
                requestId,
                traceId,
                principal == null ? null : principal.userId(),
                principal == null ? null : principal.username(),
                threadId,
                spec.presetId(),
                spec.fingerprint(),
                agentPresetService.serializeRuntimeSpec(spec),
                null,
                null,
                Instant.now()
        ), message);
    }

    private AgentRunAuditContext startRunAudit(String requestId, String traceId, RbacPrincipal principal,
                                               String threadId, AgentRuntimeSpec spec, String message,
                                               Map<String, AgentRunAuditContext.ToolDescriptor> toolDescriptors) {
        return runAuditService.start(new AgentRunAuditStart(
                requestId,
                traceId,
                principal == null ? null : principal.userId(),
                principal == null ? null : principal.username(),
                threadId,
                spec.presetId(),
                spec.fingerprint(),
                agentPresetService.serializeRuntimeSpec(spec),
                message,
                toolDescriptors,
                Instant.now()
        ));
    }

    private RunnableConfig runnableConfig(RunnableConfig config, AgentRunAuditContext context,
                                          AgentContext agentContext,
                                          AgentMemorySessionPo memorySession,
                                          AgentMemoryEntryType entryType) {
        RunnableConfig.Builder builder = RunnableConfig.builder(config);
        if (context != null) {
            context.metadata().forEach(builder::addMetadata);
        }
        builder.addMetadata("agentMemorySessionId", memorySession.getSessionId());
        builder.addMetadata("agentMemoryEntryType", entryType.name());
        if (agentContext != null) {
            agentContext.toMetadata().forEach(builder::addMetadata);
        }
        return builder.build();
    }

    private boolean shouldEmitChunk(AgentChatChunk chunk, AtomicReference<String> lastStateSnapshotKey) {
        if (chunk == null || !chunk.stateSnapshot()) {
            return true;
        }
        ChatResponse response = chunk.response();
        String key = response.type() + "\n" + response.message();
        if (key.equals(lastStateSnapshotKey.get())) {
            return false;
        }
        lastStateSnapshotKey.set(key);
        return true;
    }

    private void collectAuditChunk(AgentChatChunk chunk, AgentMemoryTextAccumulator messageContent,
                                   AgentMemoryTextAccumulator thinkContent) {
        ChatResponse response = chunk.response();
        if (response == null || response.message() == null) {
            return;
        }
        if ("think".equals(response.type())) {
            collectText(chunk, thinkContent);
            return;
        }
        if ("message".equals(response.type())) {
            collectText(chunk, messageContent);
        }
    }

    private void collectText(AgentChatChunk chunk, AgentMemoryTextAccumulator content) {
        if (chunk.stateSnapshot()) {
            content.acceptSnapshot(chunk.response().message());
            return;
        }
        content.accept(chunk.response().message());
    }

    private void finishAudit(SignalType signalType, Long auditId, AgentMemoryTextAccumulator messageContent,
                             AgentMemoryTextAccumulator thinkContent, Throwable error) {
        if (auditId == null || error != null) {
            return;
        }
        if (signalType == SignalType.CANCEL) {
            auditService.cancel(auditId, Instant.now());
            return;
        }
        if (signalType == SignalType.ON_COMPLETE) {
            List<AgentConversationMessageRecord> messages = new ArrayList<>();
            String finalThinkContent = thinkContent.normalizedContent();
            if (StringUtils.hasText(finalThinkContent)) {
                messages.add(new AgentConversationMessageRecord(AgentConversationRole.ASSISTANT,
                        AgentConversationMessageType.THINK, finalThinkContent));
            }
            String finalMessageContent = messageContent.normalizedContent();
            if (StringUtils.hasText(finalMessageContent)) {
                messages.add(new AgentConversationMessageRecord(AgentConversationRole.ASSISTANT,
                        AgentConversationMessageType.MESSAGE, finalMessageContent));
            }
            auditService.complete(auditId, messages, Instant.now());
        }
    }

    private void failAudit(Long auditId, Throwable error) {
        if (auditId == null || error == null) {
            return;
        }
        auditService.fail(auditId, error.getClass().getName(), error.getMessage(), Instant.now());
    }

    private void finishRunAudit(SignalType signalType, AgentRunAuditContext context,
                                AgentMemoryTextAccumulator messageContent,
                                AgentMemoryTextAccumulator thinkContent, Throwable error) {
        if (context == null || error != null) {
            return;
        }
        if (signalType == SignalType.CANCEL) {
            runAuditService.cancel(context, Instant.now());
            return;
        }
        if (signalType == SignalType.ON_COMPLETE) {
            runAuditService.complete(context, messageContent.normalizedContent(),
                    thinkContent.normalizedContent(), Instant.now());
        }
    }

    private void failRunAudit(AgentRunAuditContext context, Throwable error) {
        if (context == null || error == null) {
            return;
        }
        runAuditService.fail(context, error.getClass().getName(), error.getMessage(), Instant.now());
    }

    private void persistUserMemory(AgentMemorySessionPo session, String userMessage, int turnNo,
                                   String requestId, String traceId) {
        if (session == null) {
            return;
        }
        memoryMessageService.appendAll(List.of(new AgentMemoryMessageRecord(session.getSessionId(),
                session.getUserId(), session.getEntryType(), turnNo, AgentMemoryMessageRole.USER,
                AgentMemoryMessageType.MESSAGE, userMessage, null, traceId, requestId,
                AgentMemoryMessageStatus.SUCCEEDED, null, null, null)));
    }

    private void finishAssistantMemory(SignalType signalType, AgentMemorySessionPo session, int turnNo,
                                       AgentMemoryTextAccumulator messageContent,
                                       AgentMemoryTextAccumulator thinkContent,
                                       String requestId, String traceId) {
        if (session == null || signalType != SignalType.ON_COMPLETE) {
            return;
        }
        List<AgentMemoryMessageRecord> records = new ArrayList<>();
        String finalThinkContent = thinkContent.normalizedContent();
        if (StringUtils.hasText(finalThinkContent)) {
            records.add(new AgentMemoryMessageRecord(session.getSessionId(), session.getUserId(), session.getEntryType(),
                    turnNo, AgentMemoryMessageRole.ASSISTANT, AgentMemoryMessageType.THINK,
                    finalThinkContent, null, traceId, requestId,
                    AgentMemoryMessageStatus.SUCCEEDED, null, null, null));
        }
        String finalMessageContent = messageContent.normalizedContent();
        if (StringUtils.hasText(finalMessageContent)) {
            records.add(new AgentMemoryMessageRecord(session.getSessionId(), session.getUserId(), session.getEntryType(),
                    turnNo, AgentMemoryMessageRole.ASSISTANT, AgentMemoryMessageType.MESSAGE,
                    finalMessageContent, null, traceId, requestId,
                    AgentMemoryMessageStatus.SUCCEEDED, null, null, null));
        }
        if (records.isEmpty()) {
            return;
        }
        memoryMessageService.appendAll(records);
        if (session.isLongTermExtractionEnabled()) {
            memoryExtractionService.extractAfterTurn(new AgentMemoryExtractionRequest(session.getSessionId(),
                    requestId, traceId));
        }
    }

    private void failAssistantMemory(AgentMemorySessionPo session, int turnNo, String userFacingError,
                                     Throwable error, String requestId, String traceId) {
        if (session == null) {
            return;
        }
        memoryMessageService.appendAll(List.of(AgentMemoryMessageRecord.failed(session.getSessionId(),
                session.getUserId(), session.getEntryType(), turnNo, AgentMemoryMessageRole.ASSISTANT,
                AgentMemoryMessageType.ERROR, userFacingError, traceId, requestId,
                error == null ? null : error.getClass().getName(),
                error == null ? null : error.getMessage())));
    }

    private Flux<AgentChatChunk> toChatChunk(NodeOutput output, String threadId) {
        // 1) Direct message via reflection-friendly access
        try {
            Object msg = invokeOutputMethod(output, "message");
            if (msg instanceof Message m && StringUtils.hasText(m.getText())) {
                String type = inferType(output);
                return Flux.just(new AgentChatChunk(new ChatResponse(threadId, m.getText(), type), false));
            }
        } catch (Exception ignored) {
            // method not available
        }

        // 2) Fallback: extract from state
        Object state = output.state();
        Object messages = null;
        if (state instanceof OverAllState overAllState) {
            messages = overAllState.value("messages", java.util.List.class).orElse(null);
        } else if (state instanceof java.util.Map<?, ?> stateMap) {
            messages = stateMap.get("messages");
        }
        if (messages instanceof java.util.List<?> list && !list.isEmpty()) {
            Object last = list.get(list.size() - 1);
            if (!(last instanceof AssistantMessage)) {
                return Flux.empty();
            }
            String text = extractText(last);
            if (StringUtils.hasText(text)) {
                return Flux.just(new AgentChatChunk(new ChatResponse(threadId, text, "message"), true));
            }
        }

        return Flux.empty();
    }

    private record AgentChatChunk(ChatResponse response, boolean stateSnapshot) {
    }

    private String inferType(NodeOutput output) {
        try {
            Object ot = invokeOutputMethod(output, "getOutputType");
            if (ot != null && "THINKING".equalsIgnoreCase(ot.toString())) {
                return "think";
            }
        } catch (Exception ignored) {
        }
        return "message";
    }

    private Object invokeOutputMethod(NodeOutput output, String methodName) throws ReflectiveOperationException {
        Method method = output.getClass().getMethod(methodName);
        if (!method.canAccess(output)) {
            method.setAccessible(true);
        }
        return method.invoke(output);
    }

    private String extractText(Object msg) {
        if (msg instanceof AssistantMessage am) {
            return am.getText();
        }
        try {
            return (String) msg.getClass().getMethod("getText").invoke(msg);
        } catch (Exception e) {
            return msg.toString();
        }
    }

    private String resolveSessionId(String sessionId, String threadId) {
        if (StringUtils.hasText(sessionId)) {
            return sessionId.trim();
        }
        if (StringUtils.hasText(threadId)) {
            return threadId.trim();
        }
        return null;
    }

    private String errorMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return "模型调用失败：" + (StringUtils.hasText(message) ? message : "请检查模型配置后重试");
    }

}
