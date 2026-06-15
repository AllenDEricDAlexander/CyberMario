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
import top.egon.mario.agent.dto.request.AgentDebugChatRequest;
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
import top.egon.mario.pojo.response.ChatResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

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

    public ReactAgentChatService(AgentPresetService agentPresetService, AgentRuntimeFactory agentRuntimeFactory,
                                 AgentConversationAuditService auditService, AgentRunAuditService runAuditService,
                                 Scheduler blockingScheduler, ArxivToolUserContext arxivToolUserContext) {
        this.agentPresetService = agentPresetService;
        this.agentRuntimeFactory = agentRuntimeFactory;
        this.auditService = auditService;
        this.runAuditService = runAuditService;
        this.blockingScheduler = blockingScheduler;
        this.arxivToolUserContext = arxivToolUserContext;
    }

    @Override
    public Flux<ChatResponse> chat(String message, String threadId, RbacPrincipal principal) {
        return executeChat(message, threadId, principal, null);
    }

    @Override
    public Flux<ChatResponse> debugChat(AgentDebugChatRequest request, RbacPrincipal principal) {
        return executeChat(request.message(), request.threadId(), principal, request);
    }

    private Flux<ChatResponse> executeChat(String message, String threadId, RbacPrincipal principal, AgentDebugChatRequest debugRequest) {
        String conversationThreadId = resolveThreadId(threadId);

        return Flux.deferContextual(contextView -> {
            String traceId = TraceContext.traceId(contextView);
            String requestId = UUID.randomUUID().toString();
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
            List<String> messageChunks = new ArrayList<>();
            List<String> thinkChunks = new ArrayList<>();
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
                            return runtime.agent().stream(message, runnableConfig(cfg, context));
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
                    .flatMap(output -> toChatResponse(output, conversationThreadId))
                    .doOnNext(response -> collectAuditChunk(response, messageChunks, thinkChunks))
                    .doFinally(signalType -> {
                        finishAudit(signalType, auditId.get(), messageChunks, thinkChunks, null);
                        finishRunAudit(signalType, runAuditContext.get(), messageChunks, thinkChunks, null);
                    })
                    .onErrorResume(error -> {
                        failAudit(auditId.get(), error);
                        failRunAudit(runAuditContext.get(), error);
                        return Flux.just(new ChatResponse(conversationThreadId, errorMessage(error), "error"));
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

    private RunnableConfig runnableConfig(RunnableConfig config, AgentRunAuditContext context) {
        RunnableConfig.Builder builder = RunnableConfig.builder(config);
        if (context != null) {
            context.metadata().forEach(builder::addMetadata);
        }
        return builder.build();
    }

    private void collectAuditChunk(ChatResponse response, List<String> messageChunks, List<String> thinkChunks) {
        if (response == null || response.message() == null) {
            return;
        }
        if ("think".equals(response.type())) {
            thinkChunks.add(response.message());
            return;
        }
        messageChunks.add(response.message());
    }

    private void finishAudit(SignalType signalType, Long auditId, List<String> messageChunks, List<String> thinkChunks, Throwable error) {
        if (auditId == null || error != null) {
            return;
        }
        if (signalType == SignalType.CANCEL) {
            auditService.cancel(auditId, Instant.now());
            return;
        }
        if (signalType == SignalType.ON_COMPLETE) {
            List<AgentConversationMessageRecord> messages = new ArrayList<>();
            String thinkContent = String.join("", thinkChunks);
            if (StringUtils.hasText(thinkContent)) {
                messages.add(new AgentConversationMessageRecord(AgentConversationRole.ASSISTANT,
                        AgentConversationMessageType.THINK, thinkContent));
            }
            String messageContent = String.join("", messageChunks);
            if (StringUtils.hasText(messageContent)) {
                messages.add(new AgentConversationMessageRecord(AgentConversationRole.ASSISTANT,
                        AgentConversationMessageType.MESSAGE, messageContent));
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

    private void finishRunAudit(SignalType signalType, AgentRunAuditContext context, List<String> messageChunks,
                                List<String> thinkChunks, Throwable error) {
        if (context == null || error != null) {
            return;
        }
        if (signalType == SignalType.CANCEL) {
            runAuditService.cancel(context, Instant.now());
            return;
        }
        if (signalType == SignalType.ON_COMPLETE) {
            String thinkContent = normalizeContent(String.join("", thinkChunks));
            String messageContent = normalizeContent(String.join("", messageChunks));
            runAuditService.complete(context, messageContent, thinkContent, Instant.now());
        }
    }

    private void failRunAudit(AgentRunAuditContext context, Throwable error) {
        if (context == null || error == null) {
            return;
        }
        runAuditService.fail(context, error.getClass().getName(), error.getMessage(), Instant.now());
    }

    private String normalizeContent(String content) {
        return StringUtils.hasText(content) ? content : null;
    }

    private Flux<ChatResponse> toChatResponse(NodeOutput output, String threadId) {
        // 1) Direct message via reflection-friendly access
        try {
            Object msg = output.getClass().getMethod("message").invoke(output);
            if (msg instanceof Message m && StringUtils.hasText(m.getText())) {
                String type = inferType(output);
                return Flux.just(new ChatResponse(threadId, m.getText(), type));
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
                return Flux.just(new ChatResponse(threadId, text, "message"));
            }
        }

        return Flux.empty();
    }

    private String inferType(NodeOutput output) {
        try {
            Object ot = output.getClass().getMethod("getOutputType").invoke(output);
            if (ot != null && "THINKING".equalsIgnoreCase(ot.toString())) {
                return "think";
            }
        } catch (Exception ignored) {
        }
        return "message";
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

    private String resolveThreadId(String threadId) {
        if (StringUtils.hasText(threadId)) {
            return threadId.trim();
        }
        return UUID.randomUUID().toString();
    }

    private String errorMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return "模型调用失败：" + (StringUtils.hasText(message) ? message : "请检查模型配置后重试");
    }

}
