package top.egon.mario.agent.model.service.impl;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import top.egon.mario.agent.model.dto.enums.ModelProviderType;
import top.egon.mario.agent.model.dto.enums.ModelScenario;
import top.egon.mario.agent.model.dto.request.ModelOptions;
import top.egon.mario.agent.model.po.enums.ModelAuditStatus;
import top.egon.mario.agent.model.po.enums.TokenUsageSource;
import top.egon.mario.agent.model.service.ModelAuditService;
import top.egon.mario.agent.model.service.model.ModelAuditEvent;
import top.egon.mario.agent.model.service.model.ModelCallContext;
import top.egon.mario.agent.model.service.model.ModelTokenUsage;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ChatModel decorator that records audit events around model calls.
 */
public class AuditedChatModel implements ChatModel {

    private final ChatModel delegate;
    private final ModelAuditService auditService;
    private final ModelProviderType provider;
    private final String model;
    private final ModelOptions options;
    private final ModelCallContext context;

    public AuditedChatModel(ChatModel delegate, ModelAuditService auditService, ModelProviderType provider,
                            String model, ModelOptions options, ModelCallContext context) {
        this.delegate = delegate;
        this.auditService = auditService;
        this.provider = provider;
        this.model = model;
        this.options = options;
        this.context = context;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        Instant startedAt = Instant.now();
        try {
            ChatResponse response = delegate.call(prompt);
            record(prompt, responseTextLength(response), usage(response), false, ModelAuditStatus.SUCCESS,
                    startedAt, null);
            return response;
        } catch (RuntimeException e) {
            record(prompt, 0, ModelTokenUsage.unavailable(), false, ModelAuditStatus.FAILED, startedAt, e);
            throw e;
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.defer(() -> {
            Instant startedAt = Instant.now();
            AtomicInteger completionChars = new AtomicInteger();
            AtomicReference<ModelTokenUsage> tokenUsage = new AtomicReference<>(ModelTokenUsage.unavailable());
            AtomicReference<Throwable> failure = new AtomicReference<>();
            return delegate.stream(prompt)
                    .doOnNext(response -> {
                        completionChars.addAndGet(responseTextLength(response));
                        ModelTokenUsage usage = usage(response);
                        if (usage.source() == TokenUsageSource.PROVIDER) {
                            tokenUsage.set(usage);
                        }
                    })
                    .doOnError(failure::set)
                    .doFinally(signalType -> {
                        if (signalType == SignalType.CANCEL) {
                            record(prompt, completionChars.get(), tokenUsage.get(), true, ModelAuditStatus.CANCELLED,
                                    startedAt, null);
                            return;
                        }
                        Throwable error = failure.get();
                        if (error != null) {
                            record(prompt, completionChars.get(), tokenUsage.get(), true, ModelAuditStatus.FAILED,
                                    startedAt, error);
                            return;
                        }
                        record(prompt, completionChars.get(), tokenUsage.get(), true, ModelAuditStatus.SUCCESS,
                                startedAt, null);
                    });
        });
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return delegate.getDefaultOptions();
    }

    private void record(Prompt prompt, int completionChars, ModelTokenUsage tokenUsage, boolean streaming,
                        ModelAuditStatus status, Instant startedAt, Throwable error) {
        Instant finishedAt = Instant.now();
        ModelCallContext auditContext = context == null ? emptyContext() : context;
        auditService.record(new ModelAuditEvent(
                auditContext.requestId(),
                auditContext.traceId(),
                auditContext.userId(),
                auditContext.sessionId(),
                auditContext.threadId(),
                auditContext.scenario(),
                provider,
                model,
                options,
                tokenUsage,
                streaming,
                status,
                startedAt,
                finishedAt,
                Duration.between(startedAt, finishedAt).toMillis(),
                error == null ? null : error.getClass().getName(),
                error == null ? null : error.getMessage(),
                promptChars(prompt),
                completionChars,
                auditContext.ip(),
                auditContext.userAgent()
        ));
    }

    private ModelCallContext emptyContext() {
        return new ModelCallContext(null, null, null, null, ModelScenario.UNKNOWN, null, null, null);
    }

    private int promptChars(Prompt prompt) {
        if (prompt == null) {
            return 0;
        }
        return prompt.getInstructions().stream()
                .map(message -> message.getText() == null ? "" : message.getText())
                .mapToInt(String::length)
                .sum();
    }

    private int responseTextLength(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null
                || response.getResult().getOutput().getText() == null) {
            return 0;
        }
        return response.getResult().getOutput().getText().length();
    }

    private ModelTokenUsage usage(ChatResponse response) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return ModelTokenUsage.unavailable();
        }
        Usage usage = response.getMetadata().getUsage();
        Integer promptTokens = usage.getPromptTokens();
        Integer completionTokens = usage.getCompletionTokens();
        Integer totalTokens = usage.getTotalTokens();
        if (promptTokens == null && completionTokens == null && totalTokens == null) {
            return ModelTokenUsage.unavailable();
        }
        if ((promptTokens != null && promptTokens > 0) || (completionTokens != null && completionTokens > 0)
                || (totalTokens != null && totalTokens > 0)) {
            return new ModelTokenUsage(promptTokens, completionTokens, totalTokens, TokenUsageSource.PROVIDER);
        }
        return ModelTokenUsage.unavailable();
    }

}
