package top.egon.mario.agent.externalim.guard.impl;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.externalim.guard.ChatGuardAuditService;
import top.egon.mario.agent.externalim.guard.ChatGuardDecision;
import top.egon.mario.agent.externalim.guard.ChatGuardModel;
import top.egon.mario.agent.externalim.guard.ChatGuardModelInput;
import top.egon.mario.agent.externalim.guard.ChatGuardProperties;
import top.egon.mario.agent.externalim.guard.ChatGuardResult;
import top.egon.mario.agent.externalim.guard.ChatGuardService;
import top.egon.mario.agent.externalim.model.ChatInvocation;
import top.egon.mario.agent.externalim.model.ChatSource;
import top.egon.mario.agent.externalim.model.ExternalConversationType;
import top.egon.mario.agent.externalim.model.ExternalMessageType;
import top.egon.mario.agent.externalim.model.ExternalSenderType;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class DefaultChatGuardService implements ChatGuardService {

    private final ChatGuardModel model;
    private final ChatGuardAuditService auditService;
    private final ChatGuardProperties properties;
    private final ExecutorService executor;

    public DefaultChatGuardService(ChatGuardModel model, ChatGuardAuditService auditService,
                                   ChatGuardProperties properties,
                                   @Qualifier("chatGuardExecutor") ExecutorService executor) {
        this.model = model;
        this.auditService = auditService;
        this.properties = properties;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<ChatGuardResult> decide(ChatInvocation invocation,
                                                     String currentGroupWindow,
                                                     String requestId, String traceId) {
        ChatGuardResult hardDecision = hardDecision(invocation);
        CompletableFuture<ChatGuardResult> result = hardDecision == null
                ? CompletableFuture.supplyAsync(
                        () -> model.evaluate(new ChatGuardModelInput(
                                invocation, currentGroupWindow, requestId, traceId)), executor)
                    .completeOnTimeout(ChatGuardResult.ignore("chat guard model timeout"),
                            properties.timeout().toMillis(), TimeUnit.MILLISECONDS)
                    .exceptionally(error -> ChatGuardResult.ignore(
                            "chat guard model failed: " + error.getClass().getSimpleName()))
                    .thenApply(this::applyThreshold)
                : CompletableFuture.completedFuture(hardDecision);
        return result.thenApply(decision -> {
            auditService.record(invocation, decision, requestId, traceId);
            return decision;
        });
    }

    private ChatGuardResult hardDecision(ChatInvocation invocation) {
        if (invocation == null) {
            return ChatGuardResult.ignore("chat invocation is missing");
        }
        if (invocation.source() == ChatSource.WEB) {
            return ChatGuardResult.reply("web chat always replies");
        }
        if (invocation.messageType() != ExternalMessageType.TEXT
                || invocation.sender() == null
                || invocation.sender().type() != ExternalSenderType.HUMAN
                || !StringUtils.hasText(invocation.message())) {
            return ChatGuardResult.ignore("event is not a supported human text message");
        }
        if (invocation.conversationType() == ExternalConversationType.DIRECT) {
            return ChatGuardResult.reply("external direct chat always replies");
        }
        if (invocation.mentionedAgent()) {
            return ChatGuardResult.reply("group message explicitly mentions the agent");
        }
        if (invocation.repliedToAgentMessage()) {
            return ChatGuardResult.reply("group message replies to the agent");
        }
        return null;
    }

    private ChatGuardResult applyThreshold(ChatGuardResult result) {
        if (result.decision() == ChatGuardDecision.REPLY
                && result.confidence().compareTo(properties.replyThreshold()) < 0) {
            return new ChatGuardResult(ChatGuardDecision.IGNORE, result.confidence(),
                    "reply confidence below threshold: " + result.reason(),
                    result.modelProvider(), result.modelName(), result.durationMs());
        }
        return result;
    }
}
