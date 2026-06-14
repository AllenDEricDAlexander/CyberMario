package top.egon.mario.rag.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.rag.dto.response.SourceReferenceResponse;
import top.egon.mario.rag.service.RagRerankService;

import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Local fallback reranker used when no external rerank provider is configured.
 */
@Service
@Validated
public class DefaultRagRerankService implements RagRerankService {

    @Override
    public List<SourceReferenceResponse> rerank(String query, List<SourceReferenceResponse> candidates, int topK) {
        return candidates.stream()
                .sorted(Comparator.comparingDouble(SourceReferenceResponse::score).reversed())
                .map(this::markReranked)
                .limit(topK)
                .toList();
    }

    private SourceReferenceResponse markReranked(SourceReferenceResponse source) {
        Map<String, Object> metadata = new HashMap<>(source.metadata() == null ? Map.of() : source.metadata());
        metadata.put("rerank_score", source.score());
        return new SourceReferenceResponse(
                source.sourceId(),
                source.knowledgeBaseId(),
                source.knowledgeBaseName(),
                source.documentId(),
                source.documentName(),
                source.chunkId(),
                source.chunkIndex(),
                source.score(),
                source.vectorScore(),
                source.keywordScore(),
                source.fusionScore(),
                source.score(),
                source.matchedBy(),
                source.content(),
                metadata
        );
    }

}
