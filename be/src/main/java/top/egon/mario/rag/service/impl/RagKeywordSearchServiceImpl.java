package top.egon.mario.rag.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.rag.dto.response.SourceReferenceResponse;
import top.egon.mario.rag.po.RagDocumentChunkPo;
import top.egon.mario.rag.po.RagKnowledgeBasePo;
import top.egon.mario.rag.po.RagUserDocumentPo;
import top.egon.mario.rag.repository.RagDocumentChunkRepository;
import top.egon.mario.rag.repository.RagKnowledgeBaseRepository;
import top.egon.mario.rag.repository.RagUserDocumentRepository;
import top.egon.mario.rag.service.RagKeywordSearchService;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import java.util.List;

/**
 * Database-backed keyword retrieval for hybrid RAG.
 */
@Service
@RequiredArgsConstructor
@Validated
public class RagKeywordSearchServiceImpl implements RagKeywordSearchService {

    private final RagDocumentChunkRepository chunkRepository;
    private final RagKnowledgeBaseRepository knowledgeBaseRepository;
    private final RagUserDocumentRepository documentRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SourceReferenceResponse> search(String query, Collection<Long> knowledgeBaseIds, int topK) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty() || knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return List.of();
        }
        List<RagDocumentChunkPo> chunks = chunkRepository
                .findByKnowledgeBaseIdInAndEnabledTrueAndDeletedFalseAndContentContainingIgnoreCase(knowledgeBaseIds, normalizedQuery);
        Map<Long, RagKnowledgeBasePo> knowledgeBases = knowledgeBaseRepository.findAllById(knowledgeBaseIds).stream()
                .collect(Collectors.toMap(RagKnowledgeBasePo::getId, Function.identity()));
        Map<Long, RagUserDocumentPo> documents = documentRepository.findByKnowledgeBaseIdInAndDeletedFalse(knowledgeBaseIds).stream()
                .collect(Collectors.toMap(RagUserDocumentPo::getId, Function.identity(), (left, right) -> left));
        return chunks.stream()
                .map(chunk -> toSource(chunk, knowledgeBases.get(chunk.getKnowledgeBaseId()), documents.get(chunk.getDocumentId()), normalizedQuery))
                .sorted(Comparator.comparingDouble(SourceReferenceResponse::score).reversed())
                .limit(topK)
                .toList();
    }

    private SourceReferenceResponse toSource(RagDocumentChunkPo chunk, RagKnowledgeBasePo knowledgeBase,
                                             RagUserDocumentPo document, String query) {
        double score = keywordScore(chunk.getContent(), query);
        return new SourceReferenceResponse(
                String.valueOf(chunk.getId()),
                chunk.getKnowledgeBaseId(),
                knowledgeBase == null ? "" : knowledgeBase.getName(),
                chunk.getDocumentId(),
                document == null ? "" : document.getDisplayName(),
                chunk.getId(),
                chunk.getChunkIndex(),
                score,
                null,
                score,
                null,
                null,
                "KEYWORD",
                chunk.getContent(),
                Map.of("matched_by", "KEYWORD", "keyword_score", score)
        );
    }

    private double keywordScore(String content, String query) {
        String normalizedContent = content == null ? "" : content.toLowerCase();
        String normalizedQuery = query.toLowerCase();
        if (normalizedContent.isEmpty() || normalizedQuery.isEmpty()) {
            return 0D;
        }
        int count = 0;
        int index = normalizedContent.indexOf(normalizedQuery);
        while (index >= 0) {
            count++;
            index = normalizedContent.indexOf(normalizedQuery, index + normalizedQuery.length());
        }
        return Math.min(1D, 0.5D + count * 0.1D);
    }

}
