package top.egon.mario.agent.service.impl;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
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
import top.egon.mario.agent.externalim.flow.ChatAgentFlowFactory;
import top.egon.mario.agent.externalim.flow.ChatInvocationPolicy;
import top.egon.mario.agent.externalim.memory.DirectionalAgentMemoryContextService;
import top.egon.mario.agent.externalim.memory.ExternalImMemoryExtractionService;
import top.egon.mario.agent.externalim.memory.model.ExternalImMemoryExtractionRequest;
import top.egon.mario.agent.externalim.model.ChatInvocation;
import top.egon.mario.agent.memory.po.AgentMemoryMessagePo;
import top.egon.mario.agent.memory.po.AgentMemorySessionPo;
import top.egon.mario.agent.memory.po.enums.AgentMemoryDomain;
import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageRole;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageStatus;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageType;
import top.egon.mario.agent.memory.service.AgentMemoryExtractionService;
import top.egon.mario.agent.memory.service.AgentMemoryMessageService;
import top.egon.mario.agent.memory.service.AgentMemorySessionService;
import top.egon.mario.agent.memory.service.model.AgentMemoryContext;
import top.egon.mario.agent.memory.service.model.AgentMemoryExtractionRequest;
import top.egon.mario.agent.memory.service.model.AgentMemoryMessageRecord;
import top.egon.mario.agent.memory.service.model.AgentMemoryMessageSource;
import top.egon.mario.agent.memory.service.model.AgentMemoryTextAccumulator;
import top.egon.mario.agent.model.dto.enums.ModelScenario;
import top.egon.mario.agent.model.service.model.ModelCallContext;
import top.egon.mario.agent.observability.service.AgentRunAuditService;
import top.egon.mario.agent.observability.service.model.AgentRunAuditContext;
import top.egon.mario.agent.observability.service.model.AgentRunAuditStart;
import top.egon.mario.agent.po.enums.AgentConversationMessageType;
import top.egon.mario.agent.po.enums.AgentConversationRole;
import top.egon.mario.agent.service.AgentConversationAuditService;
import top.egon.mario.agent.service.AgentException;
import top.egon.mario.agent.service.AgentPresetService;
import top.egon.mario.agent.service.AgentRuntimeFactory;
import top.egon.mario.agent.service.ChatAgentService;
import top.egon.mario.agent.service.model.AgentConversationAuditStart;
import top.egon.mario.agent.service.model.AgentConversationMessageRecord;
import top.egon.mario.agent.service.model.AgentRuntimeSpec;
import top.egon.mario.agent.soul.po.enums.AgentSoulSourceType;
import top.egon.mario.agent.soul.service.AgentSoulService;
import top.egon.mario.agent.soul.service.model.AgentSoulEvolutionRequest;
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
 * streaming and exposes both reasoning (think) and final message chunks.
 */
@Slf4j
@Validated
public class ReactAgentChatService implements ChatAgentService {

    private static final String FRAMEWORK_EXCEPTION_PREFIX = "Exception:";

    private final AgentPresetService agentPresetService;
    private final AgentRuntimeFactory agentRuntimeFactory;
    private final AgentConversationAuditService auditService;
    private final AgentRunAuditService runAuditService;
    private final Scheduler blockingScheduler;
    private final ArxivToolUserContext arxivToolUserContext;
    private final AgentMemorySessionService memorySessionService;
    private final AgentMemoryMessageService memoryMessageService;
    private final DirectionalAgentMemoryContextService directionalMemoryContextService;
    private final AgentContextAssemblyService contextAssemblyService;
    private final AgentMemoryExtractionService memoryExtractionService;
    private final ExternalImMemoryExtractionService externalMemoryExtractionService;
    private final ChatInvocationPolicy invocationPolicy;
    private final ChatAgentFlowFactory flowFactory;
    private final AgentSoulService soulService;

    public ReactAgentChatService(AgentPresetService agentPresetService, AgentRuntimeFactory agentRuntimeFactory,
                                 AgentConversationAuditService auditService, AgentRunAuditService runAuditService,
                                 Scheduler blockingScheduler, ArxivToolUserContext arxivToolUserContext,
                                 AgentMemorySessionService memorySessionService,
                                 AgentMemoryMessageService memoryMessageService,
                                 DirectionalAgentMemoryContextService directionalMemoryContextService,
                                 AgentContextAssemblyService contextAssemblyService,
                                 AgentMemoryExtractionService memoryExtractionService,
                                 ExternalImMemoryExtractionService externalMemoryExtractionService,
                                 ChatInvocationPolicy invocationPolicy,
                                 ChatAgentFlowFactory flowFactory,
                                 AgentSoulService soulService) {
        this.agentPresetService = agentPresetService;
        this.agentRuntimeFactory = agentRuntimeFactory;
        this.auditService = auditService;
        this.runAuditService = runAuditService;
        this.blockingScheduler = blockingScheduler;
        this.arxivToolUserContext = arxivToolUserContext;
        this.memorySessionService = memorySessionService;
        this.memoryMessageService = memoryMessageService;
        this.directionalMemoryContextService = directionalMemoryContextService;
        this.contextAssemblyService = contextAssemblyService;
        this.memoryExtractionService = memoryExtractionService;
        this.externalMemoryExtractionService = externalMemoryExtractionService;
        this.invocationPolicy = invocationPolicy;
        this.flowFactory = flowFactory;
        this.soulService = soulService;
    }

    @Override
    public Flux<ChatResponse> chat(ChatRequest request, RbacPrincipal principal) {
        return executeChat(invocationPolicy.fromWeb(request, principal), principal, request, null);
    }

    @Override
    public Flux<ChatResponse> chat(ChatInvocation invocation) {
        return executeChat(invocationPolicy.requireExternal(invocation), null, null, null);
    }

    @Override
    public Flux<ChatResponse> debugChat(AgentDebugChatRequest request, RbacPrincipal principal) {
        ChatRequest chatRequest = new ChatRequest(request.message(), request.threadId(), request.sessionId(),
                request.memoryEnabled(), null);
        return executeChat(invocationPolicy.fromWeb(chatRequest, principal), principal, chatRequest, request);
    }

    private Flux<ChatResponse> executeChat(ChatInvocation invocation, RbacPrincipal principal,
                                           ChatRequest webRequest, AgentDebugChatRequest debugRequest) {
        return Flux.deferContextual(contextView -> {
            String traceId = TraceContext.traceId(contextView);
            String requestId = UUID.randomUUID().toString();
            String message = invocation.message();
            boolean external = invocation.externalIm();
            AgentMemoryEntryType entryType = debugRequest == null
                    ? AgentMemoryEntryType.AGENT_CHAT
                    : AgentMemoryEntryType.AGENT_DEBUG;
            AgentMemorySessionPo memorySession = external
                    ? memorySessionService.resolveOrCreateExternal(invocation.ownerUserId(), invocation.memorySpaceId())
                    : memorySessionService.resolveOrCreate(entryType, invocation.webSessionId(),
                    webRequest.memoryContextEnabled(),
                    debugRequest == null ? Boolean.TRUE : debugRequest.longTermExtractionEnabled(), principal);
            if (external) {
                memorySession.setUsername(invocation.ownerUsername());
            }
            boolean memoryContextEnabled = memorySession.isMemoryEnabled();
            String conversationThreadId = memorySession.getSessionId();
            int nextTurnNo = memoryMessageService.nextTurnNo(conversationThreadId);
            AgentMemoryMessagePo externalUserMessage = external
                    ? persistExternalObservation(invocation, memorySession, nextTurnNo, requestId, traceId)
                    : null;
            int turnNo = external && externalUserMessage != null
                    ? externalUserMessage.getTurnNo()
                    : nextTurnNo;
            AgentMemoryContext memoryContext = external
                    ? directionalMemoryContextService.externalContext(memorySession,
                    externalUserMessage == null ? null : externalUserMessage.getId(), memoryContextEnabled)
                    : directionalMemoryContextService.webContext(memorySession, principal,
                    invocation.memorySpaceId(), memoryContextEnabled);
            if (!external) {
                persistUserMemory(memorySession, message, turnNo, requestId, traceId,
                        AgentMemoryMessageSource.webPrivate());
            }
            String guardGroupWindow = external
                    ? directionalMemoryContextService.guardGroupWindow(
                    invocation, externalUserMessage == null ? null : externalUserMessage.getId())
                    : "";
            AgentContext agentContext = contextAssemblyService.assemble(
                    external ? null : principal, memoryContext, !external && debugRequest == null);
            AgentRuntimeSpec spec = external
                    ? agentPresetService.externalImRuntimeSpec()
                    : debugRequest == null
                    ? agentPresetService.defaultRuntimeSpec()
                    : agentPresetService.resolveRuntimeSpec(debugRequest);
            ModelCallContext modelCallContext = new ModelCallContext(
                    invocation.ownerUserId(),
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
            AgentMemoryTextAccumulator messageContent = new AgentMemoryTextAccumulator();
            AgentMemoryTextAccumulator thinkContent = new AgentMemoryTextAccumulator();
            AtomicReference<String> lastStateSnapshotKey = new AtomicReference<>();
            TraceContext.withMdc(traceId, () -> LogUtil.info(log).log("agent chat started, threadId={}, messageLength={}",
                    conversationThreadId, message == null ? 0 : message.length()));
            Flux<ChatResponse> response = Mono.just(RunnableConfig.builder().threadId(conversationThreadId).build())
                    .flatMapMany(cfg -> {
                        auditId.set(startAudit(requestId, traceId, invocation, conversationThreadId, spec, message));
                        AgentRunAuditContext context = startRunAudit(requestId, traceId, invocation,
                                conversationThreadId, spec, message, runtime.toolDescriptors());
                        runAuditContext.set(context);
                        if (!external) {
                            arxivToolUserContext.set(principal);
                        }
                        RunnableConfig effectiveConfig = runnableConfig(cfg, context, agentContext,
                                memorySession, entryType);
                        return flowFactory.stream(invocation, runtime.agent(), effectiveConfig,
                                guardGroupWindow, requestId, traceId);
                    })
                    .doOnComplete(() -> TraceContext.withMdc(traceId,
                            () -> LogUtil.info(log).log("agent chat completed, threadId={}", conversationThreadId)))
                    .doOnError(error -> TraceContext.withMdc(traceId,
                            () -> LogUtil.error(log).log("agent chat failed, threadId={}", conversationThreadId, error)))
                    .flatMap(output -> toChatChunk(output, conversationThreadId))
                    .filter(chunk -> shouldEmitChunk(chunk, lastStateSnapshotKey, messageContent, thinkContent))
                    .doOnNext(chunk -> collectAuditChunk(chunk, messageContent, thinkContent))
                    .map(AgentChatChunk::response);
            if (external) {
                response = response.concatWith(Flux.defer(() -> {
                    finishExternalAssistantMemory(memorySession, externalUserMessage, turnNo,
                            messageContent, thinkContent, requestId, traceId, invocation);
                    return Flux.empty();
                }));
            }
            return response
                    .doFinally(signalType -> {
                        if (!external) {
                            arxivToolUserContext.clear();
                        }
                        finishAudit(signalType, auditId.get(), messageContent, thinkContent, null);
                        finishRunAudit(signalType, runAuditContext.get(), messageContent, thinkContent, null);
                        if (!external) {
                            finishAssistantMemory(signalType, memorySession, turnNo, messageContent, thinkContent,
                                    requestId, traceId, AgentMemoryMessageSource.webPrivate(), true);
                            maybeEvolveSoulAfterChat(signalType, entryType, memorySession, principal, message,
                                    messageContent, agentContext, requestId, traceId);
                        }
                    })
                    .onErrorResume(error -> {
                        String userFacingError = errorMessage(error);
                        failAudit(auditId.get(), error);
                        failRunAudit(runAuditContext.get(), error);
                        failAssistantMemory(memorySession, turnNo, userFacingError, error, requestId, traceId,
                                external ? externalSource(invocation, false, true)
                                        : AgentMemoryMessageSource.webPrivate());
                        return Flux.just(new ChatResponse(conversationThreadId, userFacingError, "error"));
                    });
        }).subscribeOn(blockingScheduler);
    }

    private Long startAudit(String requestId, String traceId, ChatInvocation invocation, String threadId,
                            AgentRuntimeSpec spec, String message) {
        return auditService.start(new AgentConversationAuditStart(
                requestId,
                traceId,
                invocation.ownerUserId(),
                invocation.ownerUsername(),
                threadId,
                spec.presetId(),
                spec.fingerprint(),
                agentPresetService.serializeRuntimeSpec(spec),
                null,
                null,
                Instant.now()
        ), message);
    }

    private AgentRunAuditContext startRunAudit(String requestId, String traceId, ChatInvocation invocation,
                                               String threadId, AgentRuntimeSpec spec, String message,
                                               Map<String, AgentRunAuditContext.ToolDescriptor> toolDescriptors) {
        return runAuditService.start(new AgentRunAuditStart(
                requestId,
                traceId,
                invocation.ownerUserId(),
                invocation.ownerUsername(),
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

    private boolean shouldEmitChunk(AgentChatChunk chunk, AtomicReference<String> lastStateSnapshotKey,
                                    AgentMemoryTextAccumulator messageContent,
                                    AgentMemoryTextAccumulator thinkContent) {
        if (chunk == null) {
            return false;
        }
        if (!chunk.stateSnapshot()) {
            return true;
        }
        ChatResponse response = chunk.response();
        if (response == null || isAlreadyAccumulatedSnapshot(response, messageContent, thinkContent)) {
            return false;
        }
        String key = response.type() + "\n" + response.message();
        if (key.equals(lastStateSnapshotKey.get())) {
            return false;
        }
        lastStateSnapshotKey.set(key);
        return true;
    }

    private boolean isAlreadyAccumulatedSnapshot(ChatResponse response,
                                                 AgentMemoryTextAccumulator messageContent,
                                                 AgentMemoryTextAccumulator thinkContent) {
        if (response.message() == null) {
            return false;
        }
        if ("think".equals(response.type())) {
            return response.message().equals(thinkContent.content());
        }
        if ("message".equals(response.type())) {
            return response.message().equals(messageContent.content());
        }
        return false;
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

    private AgentMemoryMessagePo persistUserMemory(AgentMemorySessionPo session, String userMessage, int turnNo,
                                                   String requestId, String traceId,
                                                   AgentMemoryMessageSource source) {
        if (session == null) {
            return null;
        }
        List<AgentMemoryMessagePo> saved = memoryMessageService.appendAll(List.of(new AgentMemoryMessageRecord(
                session.getSessionId(),
                session.getUserId(), session.getEntryType(), turnNo, AgentMemoryMessageRole.USER,
                AgentMemoryMessageType.MESSAGE, userMessage, null, traceId, requestId,
                AgentMemoryMessageStatus.SUCCEEDED, null, null, null, source)));
        return saved.isEmpty() ? null : saved.getFirst();
    }

    private AgentMemoryMessagePo persistExternalObservation(
            ChatInvocation invocation, AgentMemorySessionPo session, int turnNo,
            String requestId, String traceId) {
        return memoryMessageService.findExternalMessage(
                        invocation.memorySpaceId(), invocation.platform(), invocation.connectorId(),
                        invocation.eventId(), AgentMemoryMessageRole.USER,
                        AgentMemoryMessageType.MESSAGE, AgentMemoryMessageStatus.SUCCEEDED)
                .orElseGet(() -> persistUserMemory(session, invocation.message(), turnNo,
                        requestId, traceId, externalSource(invocation, true, false)));
    }

    private List<AgentMemoryMessagePo> finishAssistantMemory(
            SignalType signalType, AgentMemorySessionPo session, int turnNo,
            AgentMemoryTextAccumulator messageContent, AgentMemoryTextAccumulator thinkContent,
            String requestId, String traceId, AgentMemoryMessageSource source,
            boolean standardExtraction) {
        if (session == null || signalType != SignalType.ON_COMPLETE) {
            return List.of();
        }
        List<AgentMemoryMessageRecord> records = new ArrayList<>();
        String finalThinkContent = thinkContent.normalizedContent();
        if (StringUtils.hasText(finalThinkContent)) {
            records.add(new AgentMemoryMessageRecord(session.getSessionId(), session.getUserId(), session.getEntryType(),
                    turnNo, AgentMemoryMessageRole.ASSISTANT, AgentMemoryMessageType.THINK,
                    finalThinkContent, null, traceId, requestId,
                    AgentMemoryMessageStatus.SUCCEEDED, null, null, null, source));
        }
        String finalMessageContent = messageContent.normalizedContent();
        if (StringUtils.hasText(finalMessageContent)) {
            records.add(new AgentMemoryMessageRecord(session.getSessionId(), session.getUserId(), session.getEntryType(),
                    turnNo, AgentMemoryMessageRole.ASSISTANT, AgentMemoryMessageType.MESSAGE,
                    finalMessageContent, null, traceId, requestId,
                    AgentMemoryMessageStatus.SUCCEEDED, null, null, null, source));
        }
        if (records.isEmpty()) {
            return List.of();
        }
        List<AgentMemoryMessagePo> saved = memoryMessageService.appendAll(records);
        if (standardExtraction && session.isLongTermExtractionEnabled()) {
            memoryExtractionService.extractAfterTurn(new AgentMemoryExtractionRequest(session.getSessionId(),
                    requestId, traceId));
        }
        return saved;
    }

    private void finishExternalAssistantMemory(
            AgentMemorySessionPo session, AgentMemoryMessagePo userMessage, int turnNo,
            AgentMemoryTextAccumulator messageContent, AgentMemoryTextAccumulator thinkContent,
            String requestId, String traceId, ChatInvocation invocation) {
        List<AgentMemoryMessagePo> saved = finishAssistantMemory(SignalType.ON_COMPLETE, session, turnNo,
                messageContent, thinkContent, requestId, traceId,
                externalSource(invocation, false, true), false);
        AgentMemoryMessagePo assistantMessage = saved.stream()
                .filter(row -> row.getMessageType() == AgentMemoryMessageType.MESSAGE)
                .findFirst()
                .orElse(null);
        if (userMessage == null || assistantMessage == null) {
            return;
        }
        memoryMessageService.markResponded(userMessage.getId());
        if (!session.isLongTermExtractionEnabled()) {
            return;
        }
        try {
            externalMemoryExtractionService.extractAfterReply(new ExternalImMemoryExtractionRequest(
                    session, userMessage, assistantMessage, requestId, traceId));
        } catch (Exception error) {
            TraceContext.withMdc(traceId, () -> LogUtil.warn(log)
                    .log("external IM memory extraction failed, threadId={}", session.getSessionId(), error));
        }
    }

    private void maybeEvolveSoulAfterChat(SignalType signalType, AgentMemoryEntryType currentEntryType,
                                          AgentMemorySessionPo session,
                                          RbacPrincipal principal, String userMessage,
                                          AgentMemoryTextAccumulator messageContent,
                                          AgentContext agentContext, String requestId, String traceId) {
        if (signalType != SignalType.ON_COMPLETE || currentEntryType != AgentMemoryEntryType.AGENT_CHAT
                || session == null) {
            return;
        }
        String finalMessageContent = messageContent.normalizedContent();
        if (!StringUtils.hasText(finalMessageContent)) {
            return;
        }
        try {
            soulService.maybeEvolveAfterChat(new AgentSoulEvolutionRequest(
                    principal,
                    session.getSessionId(),
                    userMessage,
                    finalMessageContent,
                    agentContext == null ? "" : agentContext.recentTurnsPrompt(),
                    AgentSoulSourceType.AGENT_CHAT,
                    requestId,
                    traceId
            ));
        } catch (Exception error) {
            TraceContext.withMdc(traceId, () -> LogUtil.warn(log)
                    .log("agent soul evolution failed, threadId={}", session.getSessionId(), error));
        }
    }

    private void failAssistantMemory(AgentMemorySessionPo session, int turnNo, String userFacingError,
                                     Throwable error, String requestId, String traceId,
                                     AgentMemoryMessageSource source) {
        if (session == null) {
            return;
        }
        memoryMessageService.appendAll(List.of(new AgentMemoryMessageRecord(session.getSessionId(),
                session.getUserId(), session.getEntryType(), turnNo, AgentMemoryMessageRole.ASSISTANT,
                AgentMemoryMessageType.ERROR, userFacingError, null, traceId, requestId,
                AgentMemoryMessageStatus.FAILED,
                error == null ? null : error.getClass().getName(),
                error == null ? null : error.getMessage(), null, source)));
    }

    private AgentMemoryMessageSource externalSource(ChatInvocation invocation, boolean observedOnly,
                                                    boolean assistant) {
        return new AgentMemoryMessageSource(
                AgentMemoryDomain.IM_SHARED,
                invocation.memorySpaceId(), invocation.platform(), invocation.connectorId(),
                invocation.conversationId(), invocation.conversationType(), invocation.audienceKey(),
                invocation.eventId(), invocation.messageId(),
                assistant || invocation.sender() == null ? null : invocation.sender().id(),
                assistant ? "Agent" : invocation.sender() == null ? null : invocation.sender().displayName(),
                observedOnly);
    }

    private Flux<AgentChatChunk> toChatChunk(NodeOutput output, String threadId) {
        // 1) Direct message via reflection-friendly access
        try {
            Object msg = invokeOutputMethod(output, "message");
            if (msg instanceof Message m && StringUtils.hasText(m.getText())) {
                String type = inferType(output);
                return chatChunk(threadId, m.getText(), type, false);
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
                return chatChunk(threadId, text, "message", true);
            }
        }

        return Flux.empty();
    }

    private Flux<AgentChatChunk> chatChunk(String threadId, String text, String type, boolean stateSnapshot) {
        RuntimeException frameworkError = frameworkError(text);
        if (frameworkError != null) {
            return Flux.error(frameworkError);
        }
        return Flux.just(new AgentChatChunk(new ChatResponse(threadId, text, type), stateSnapshot));
    }

    private RuntimeException frameworkError(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith(FRAMEWORK_EXCEPTION_PREFIX)) {
            return null;
        }
        String message = trimmed.substring(FRAMEWORK_EXCEPTION_PREFIX.length()).trim();
        return new AgentException("MODEL_CALL_FAILED",
                StringUtils.hasText(message) ? message : "请检查模型配置后重试");
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

    private String errorMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return "模型调用失败：" + (StringUtils.hasText(message) ? message : "请检查模型配置后重试");
    }

}
