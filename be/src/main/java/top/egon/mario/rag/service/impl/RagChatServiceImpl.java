package top.egon.mario.rag.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.rag.config.RagProperties;
import top.egon.mario.rag.dto.request.RagChatRequest;
import top.egon.mario.rag.dto.response.RagStreamEvent;
import top.egon.mario.rag.dto.response.SourceReferenceResponse;
import top.egon.mario.rag.service.RagChatService;
import top.egon.mario.rag.service.RagRetrievalService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

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

    @Override
    public Flux<RagStreamEvent> stream(RagChatRequest request, RbacPrincipal principal) {
        String messageId = UUID.randomUUID().toString();
        return Flux.deferContextual(contextView -> {
            String traceId = TraceContext.traceId(contextView);
            List<SourceReferenceResponse> sources = retrieveSources(request, principal);
            Flux<RagStreamEvent> header = Flux.just(
                    event("metadata", Map.of("messageId", messageId, "traceId", traceId)),
                    event("retrieval", Map.of("sources", sources, "topK", actualTopK(request)))
            );
            if (sources.isEmpty()) {
                return header.concatWithValues(
                        event("delta", Map.of("content", "知识库中没有找到明确依据。")),
                        event("done", Map.of("finishReason", "NO_CONTEXT"))
                );
            }
            Prompt prompt = new Prompt(
                    new SystemMessage(systemPrompt()),
                    new UserMessage(userPrompt(request.question(), sources))
            );
            Flux<RagStreamEvent> deltas = chatModel.stream(prompt)
                    .map(response -> response.getResult().getOutput().getText())
                    .filter(content -> content != null && !content.isBlank())
                    .map(content -> event("delta", Map.of("content", content)));
            return header.concatWith(deltas).concatWithValues(event("done", Map.of("finishReason", "STOP")));
        });
    }

    private List<SourceReferenceResponse> retrieveSources(RagChatRequest request, RbacPrincipal principal) {
        RagChatRequest.RetrievalOptions options = request.retrievalOptions();
        return retrievalService.searchSources(
                request.question(),
                request.knowledgeBaseIds(),
                options == null ? null : options.topK(),
                options == null ? null : options.similarityThreshold(),
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

    private String userPrompt(String question, List<SourceReferenceResponse> sources) {
        String context = sources.stream()
                .map(source -> "[来源 " + source.sourceId() + "] " + source.content())
                .collect(Collectors.joining("\n\n"));
        return """
                知识库上下文：
                %s
                
                用户问题：
                %s
                """.formatted(context, question);
    }

    private RagStreamEvent event(String type, Map<String, Object> data) {
        return new RagStreamEvent(type, data);
    }

}
