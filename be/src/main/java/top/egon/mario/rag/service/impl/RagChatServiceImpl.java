package top.egon.mario.rag.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
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
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rag.config.RagProperties;
import top.egon.mario.rag.dto.request.RagChatRequest;
import top.egon.mario.rag.dto.response.RagStreamEvent;
import top.egon.mario.rag.dto.response.SourceReferenceResponse;
import top.egon.mario.rag.service.RagChatService;
import top.egon.mario.rag.service.RagRetrievalService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Streams RAG metadata, retrieved sources and model deltas as JSON line events.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Validated
public class RagChatServiceImpl implements RagChatService {

    private final RagProperties properties;
    private final RagRetrievalService retrievalService;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final AgentMemorySessionService memorySessionService;
    private final AgentMemoryMessageService memoryMessageService;
    private final AgentMemoryContextService memoryContextService;
    private final AgentMemoryExtractionService memoryExtractionService;

    @Override
    public Flux<RagStreamEvent> stream(RagChatRequest request, RbacPrincipal principal) {
        String messageId = UUID.randomUUID().toString();
        return Flux.deferContextual(contextView -> {
            String traceId = TraceContext.traceId(contextView);
            AgentMemorySessionPo session = memorySessionService.resolveOrCreate(
                    AgentMemoryEntryType.RAG_CHAT,
                    request.sessionId(),
                    request.memoryContextEnabled(),
                    request.longTermExtractionEnabled(),
                    principal);
            AgentMemoryContext memoryContext = memoryContextService.contextFor(session, principal, false);
            int turnNo = memoryMessageService.nextTurnNo(session.getSessionId());
            persistUserMemory(session, request, turnNo, traceId, messageId);
            Flux<RagStreamEvent> metadata = Flux.just(event("metadata", Map.of(
                    "messageId", messageId,
                    "traceId", traceId,
                    "sessionId", session.getSessionId(),
                    "memoryContextEnabled", session.isMemoryEnabled(),
                    "memoryEnabled", session.isMemoryEnabled(),
                    "longTermExtractionEnabled", session.isLongTermExtractionEnabled())));
            return metadata.concatWith(Flux.defer(() -> ragEvents(request, principal, session, memoryContext,
                    turnNo, traceId, messageId)));
        });
    }

    private Flux<RagStreamEvent> ragEvents(RagChatRequest request, RbacPrincipal principal,
                                           AgentMemorySessionPo session, AgentMemoryContext memoryContext,
                                           int turnNo, String traceId, String messageId) {
        List<SourceReferenceResponse> sources;
        try {
            sources = retrieveSources(request, principal);
        } catch (Exception error) {
            return failAssistantEvents(session, turnNo, error, traceId, messageId);
        }
        Flux<RagStreamEvent> retrieval = Flux.just(event("retrieval", Map.of("sources", sources,
                "topK", actualTopK(request))));
        if (sources.isEmpty()) {
            String noContextAnswer = "知识库中没有找到明确依据。";
            AgentMemoryTextAccumulator assistantContent = new AgentMemoryTextAccumulator();
            assistantContent.accept(noContextAnswer);
            return retrieval.concatWithValues(event("delta", Map.of("content", noContextAnswer)))
                    .concatWith(Flux.defer(() -> {
                        finishAssistantMemory(session, turnNo, assistantContent, sources, traceId, messageId);
                        return Flux.just(event("done", Map.of("finishReason", "NO_CONTEXT")));
                    }));
        }
        AgentMemoryTextAccumulator assistantContent = new AgentMemoryTextAccumulator();
        Flux<RagStreamEvent> modelEvents = Flux.defer(() -> {
            Prompt prompt = new Prompt(
                    new SystemMessage(systemPrompt()),
                    new UserMessage(userPrompt(request.question(), sources, memoryContext.shortTermPrompt()))
            );
            return chatModel.stream(prompt)
                    .map(response -> response.getResult().getOutput().getText())
                    .filter(content -> content != null && !content.isBlank())
                    .doOnNext(assistantContent::accept)
                    .map(content -> event("delta", Map.of("content", content)));
        });
        return retrieval.concatWith(modelEvents.materialize().concatMap(signal -> {
            if (signal.isOnNext()) {
                return Flux.just(signal.get());
            }
            if (signal.isOnComplete()) {
                return Flux.defer(() -> {
                    finishAssistantMemory(session, turnNo, assistantContent, sources, traceId, messageId);
                    return Flux.just(event("done", Map.of("finishReason", "STOP")));
                });
            }
            return failAssistantEvents(session, turnNo, signal.getThrowable(), traceId, messageId);
        }));
    }

    private List<SourceReferenceResponse> retrieveSources(RagChatRequest request, RbacPrincipal principal) {
        RagChatRequest.RetrievalOptions options = request.retrievalOptions();
        return retrievalService.searchSources(
                request.question(),
                request.knowledgeBaseIds(),
                options == null ? null : options.topK(),
                options == null ? null : options.candidateTopK(),
                options == null ? null : options.similarityThreshold(),
                options == null ? null : options.searchMode(),
                options == null ? null : options.rerankEnabled(),
                principal
        );
    }

    private int actualTopK(RagChatRequest request) {
        if (request.retrievalOptions() != null && request.retrievalOptions().topK() != null) {
            return request.retrievalOptions().topK();
        }
        return properties.retrieval().defaultTopK();
    }

    private String systemPrompt() {
        return """
                你是 CyberMario 的企业知识库问答助手。
                你必须优先根据提供的知识库上下文回答。
                如果上下文没有明确依据，请直接说：知识库中没有找到明确依据。
                不要编造来源、接口名称、配置项或版本号。
                回答要简洁、准确、结构清晰。
                """;
    }

    private String userPrompt(String question, List<SourceReferenceResponse> sources, String shortTermPrompt) {
        String context = sources.stream()
                .map(source -> "[来源 " + source.sourceId() + "] " + source.content())
                .collect(Collectors.joining("\n\n"));
        String memorySection = shortTermPrompt == null || shortTermPrompt.isBlank()
                ? "无"
                : shortTermPrompt;
        return "当前 RAG 会话最近对话：\n%s\n\n知识库上下文：\n%s\n\n用户问题：\n%s"
                .formatted(memorySection, context, question);
    }

    private void persistUserMemory(AgentMemorySessionPo session, RagChatRequest request, int turnNo,
                                   String traceId, String requestId) {
        memoryMessageService.appendAll(List.of(new AgentMemoryMessageRecord(
                session.getSessionId(),
                session.getUserId(),
                AgentMemoryEntryType.RAG_CHAT,
                turnNo,
                AgentMemoryMessageRole.USER,
                AgentMemoryMessageType.MESSAGE,
                request.question(),
                null,
                traceId,
                requestId,
                AgentMemoryMessageStatus.SUCCEEDED,
                null,
                null,
                null)));
    }

    private void finishAssistantMemory(AgentMemorySessionPo session, int turnNo,
                                       AgentMemoryTextAccumulator assistantContent,
                                       List<SourceReferenceResponse> sources,
                                       String traceId, String requestId) {
        String finalContent = assistantContent.normalizedContent();
        if (finalContent == null) {
            return;
        }
        memoryMessageService.appendAll(List.of(new AgentMemoryMessageRecord(
                session.getSessionId(),
                session.getUserId(),
                AgentMemoryEntryType.RAG_CHAT,
                turnNo,
                AgentMemoryMessageRole.ASSISTANT,
                AgentMemoryMessageType.MESSAGE,
                finalContent,
                sourceRefsJson(sources),
                traceId,
                requestId,
                AgentMemoryMessageStatus.SUCCEEDED,
                null,
                null,
                null)));
        if (session.isLongTermExtractionEnabled()) {
            try {
                memoryExtractionService.extractAfterTurn(new AgentMemoryExtractionRequest(
                        session.getSessionId(), requestId, traceId));
            } catch (Exception e) {
                LogUtil.warn(log).log("rag long-term memory extraction failed, sessionId={}, requestId={}",
                        session.getSessionId(), requestId, e);
            }
        }
    }

    private void failAssistantMemory(AgentMemorySessionPo session, int turnNo, String userFacingError,
                                     Throwable error, String traceId, String requestId) {
        memoryMessageService.appendAll(List.of(AgentMemoryMessageRecord.failed(
                session.getSessionId(),
                session.getUserId(),
                AgentMemoryEntryType.RAG_CHAT,
                turnNo,
                AgentMemoryMessageRole.ASSISTANT,
                AgentMemoryMessageType.ERROR,
                userFacingError,
                traceId,
                requestId,
                error == null ? null : error.getClass().getName(),
                error == null ? null : error.getMessage())));
    }

    private Flux<RagStreamEvent> failAssistantEvents(AgentMemorySessionPo session, int turnNo, Throwable error,
                                                     String traceId, String requestId) {
        return Flux.defer(() -> {
            String userFacingError = errorMessage(error);
            failAssistantMemory(session, turnNo, userFacingError, error, traceId, requestId);
            return Flux.just(errorEvent(error, userFacingError, traceId, session.getSessionId()));
        });
    }

    private String errorMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return "模型调用失败：" + (message == null || message.isBlank() ? "请检查模型配置后重试" : message);
    }

    private String sourceRefsJson(List<SourceReferenceResponse> sources) {
        if (sources == null || sources.isEmpty()) {
            return "[]";
        }
        List<Map<String, Object>> refs = sources.stream()
                .map(source -> {
                    Map<String, Object> ref = new LinkedHashMap<>();
                    ref.put("sourceId", source.sourceId());
                    ref.put("knowledgeBaseId", source.knowledgeBaseId());
                    ref.put("documentId", source.documentId());
                    ref.put("chunkId", source.chunkId());
                    return ref;
                })
                .toList();
        try {
            return objectMapper.writeValueAsString(refs);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private RagStreamEvent event(String type, Map<String, Object> data) {
        return new RagStreamEvent(type, data);
    }

    private RagStreamEvent errorEvent(Throwable error, String userFacingError, String traceId, String sessionId) {
        return event("error", Map.of(
                "code", error == null ? "UNKNOWN" : error.getClass().getName(),
                "message", userFacingError,
                "traceId", traceId,
                "sessionId", sessionId));
    }

}
