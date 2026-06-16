package top.egon.mario.rag.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageType;
import top.egon.mario.agent.memory.service.AgentMemoryContextService;
import top.egon.mario.agent.memory.service.AgentMemoryExtractionService;
import top.egon.mario.agent.memory.service.AgentMemoryMessageService;
import top.egon.mario.agent.memory.service.AgentMemorySessionService;
import top.egon.mario.agent.memory.service.model.AgentMemoryContext;
import top.egon.mario.agent.memory.service.model.AgentMemoryExtractionRequest;
import top.egon.mario.agent.memory.service.model.AgentMemoryMessageRecord;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.rag.config.RagProperties;
import top.egon.mario.rag.dto.request.RagChatRequest;
import top.egon.mario.rag.dto.response.RagStreamEvent;
import top.egon.mario.rag.dto.response.SourceReferenceResponse;
import top.egon.mario.rag.service.RagChatService;
import top.egon.mario.rag.service.RagRetrievalService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Streams RAG metadata, retrieved sources and model deltas as JSON line events.
 */
@Service
@RequiredArgsConstructor
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
                    request.memoryEnabled(),
                    request.longTermExtractionEnabled(),
                    principal);
            AgentMemoryContext memoryContext = memoryContextService.contextFor(session, principal);
            List<SourceReferenceResponse> sources = retrieveSources(request, principal);
            Flux<RagStreamEvent> header = Flux.just(
                    event("metadata", Map.of(
                            "messageId", messageId,
                            "traceId", traceId,
                            "sessionId", session.getSessionId(),
                            "memoryEnabled", session.isMemoryEnabled(),
                            "longTermExtractionEnabled", session.isLongTermExtractionEnabled())),
                    event("retrieval", Map.of("sources", sources, "topK", actualTopK(request)))
            );
            if (sources.isEmpty()) {
                String noContextAnswer = "知识库中没有找到明确依据。";
                return header.concatWithValues(
                        event("delta", Map.of("content", noContextAnswer)),
                        event("done", Map.of("finishReason", "NO_CONTEXT"))
                ).doOnComplete(() -> finishMemory(session, request, noContextAnswer, sources, traceId, messageId));
            }
            Prompt prompt = new Prompt(
                    new SystemMessage(systemPrompt()),
                    new UserMessage(userPrompt(request.question(), sources, memoryContext.shortTermPrompt()))
            );
            List<String> assistantDeltas = new ArrayList<>();
            Flux<RagStreamEvent> deltas = chatModel.stream(prompt)
                    .map(response -> response.getResult().getOutput().getText())
                    .filter(content -> content != null && !content.isBlank())
                    .doOnNext(assistantDeltas::add)
                    .map(content -> event("delta", Map.of("content", content)));
            return header.concatWith(deltas).concatWithValues(event("done", Map.of("finishReason", "STOP")))
                    .doOnComplete(() -> finishMemory(session, request, String.join("", assistantDeltas),
                            sources, traceId, messageId));
        });
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

    private void finishMemory(AgentMemorySessionPo session, RagChatRequest request, String assistantContent,
                              List<SourceReferenceResponse> sources, String traceId, String requestId) {
        int turnNo = memoryMessageService.nextTurnNo(session.getSessionId());
        memoryMessageService.appendAll(List.of(
                new AgentMemoryMessageRecord(
                        session.getSessionId(),
                        session.getUserId(),
                        AgentMemoryEntryType.RAG_CHAT,
                        turnNo,
                        AgentMemoryMessageRole.USER,
                        AgentMemoryMessageType.MESSAGE,
                        request.question(),
                        null,
                        traceId,
                        requestId),
                new AgentMemoryMessageRecord(
                        session.getSessionId(),
                        session.getUserId(),
                        AgentMemoryEntryType.RAG_CHAT,
                        turnNo,
                        AgentMemoryMessageRole.ASSISTANT,
                        AgentMemoryMessageType.MESSAGE,
                        assistantContent,
                        sourceRefsJson(sources),
                        traceId,
                        requestId)
        ));
        if (session.isLongTermExtractionEnabled()) {
            memoryExtractionService.extractAfterTurn(new AgentMemoryExtractionRequest(
                    session.getSessionId(), requestId, traceId));
        }
    }

    private String sourceRefsJson(List<SourceReferenceResponse> sources) {
        if (sources == null || sources.isEmpty()) {
            return "[]";
        }
        List<Map<String, Object>> refs = sources.stream()
                .map(source -> Map.<String, Object>of(
                        "sourceId", source.sourceId(),
                        "knowledgeBaseId", source.knowledgeBaseId(),
                        "documentId", source.documentId(),
                        "chunkId", source.chunkId()
                ))
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

}
