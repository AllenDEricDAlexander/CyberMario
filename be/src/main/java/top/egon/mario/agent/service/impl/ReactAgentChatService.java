package top.egon.mario.agent.service.impl;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import top.egon.mario.agent.service.ChatAgentService;
import top.egon.mario.agent.tools.arxiv.ArxivToolUserContext;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.pojo.response.ChatResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.UUID;

/**
 * Reactive adapter around Spring AI Alibaba ReactAgent's streaming API.
 * <p>
 * Uses {@link ReactAgent#stream(String, RunnableConfig)} for token-level
 * streaming and exposes both reasoning (think) and final message chunks.
 */
@Slf4j
@Validated
public class ReactAgentChatService implements ChatAgentService {

    private final ReactAgent agent;
    private final Scheduler blockingScheduler;
    private final ArxivToolUserContext arxivToolUserContext;

    public ReactAgentChatService(ReactAgent agent, Scheduler blockingScheduler, ArxivToolUserContext arxivToolUserContext) {
        this.agent = agent;
        this.blockingScheduler = blockingScheduler;
        this.arxivToolUserContext = arxivToolUserContext;
    }

    @Override
    public Flux<ChatResponse> chat(String message, String threadId, RbacPrincipal principal) {
        String conversationThreadId = resolveThreadId(threadId);
        RunnableConfig config = RunnableConfig.builder()
                .threadId(conversationThreadId)
                .build();

        return Flux.deferContextual(contextView -> {
            String traceId = TraceContext.traceId(contextView);
            TraceContext.withMdc(traceId, () -> LogUtil.info(log).log("agent chat started, threadId={}, messageLength={}",
                    conversationThreadId, message == null ? 0 : message.length()));
            return Mono.just(config)
                    .flatMapMany(cfg -> {
                        try {
                            arxivToolUserContext.set(principal);
                            return agent.stream(message, cfg);
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
                    .flatMap(output -> toChatResponse(output, conversationThreadId));
        });
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
        if (state instanceof java.util.Map<?, ?> stateMap) {
            Object messages = stateMap.get("messages");
            if (messages instanceof java.util.List<?> list && !list.isEmpty()) {
                Object last = list.get(list.size() - 1);
                String text = extractText(last);
                if (StringUtils.hasText(text)) {
                    return Flux.just(new ChatResponse(threadId, text, "message"));
                }
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

}
