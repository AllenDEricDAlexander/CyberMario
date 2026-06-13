package top.egon.mario.rag.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.rag.config.RagProperties;
import top.egon.mario.rag.dto.request.RetrievalSearchRequest;
import top.egon.mario.rag.dto.response.RetrievalSearchResponse;
import top.egon.mario.rag.dto.response.SourceReferenceResponse;
import top.egon.mario.rag.service.RagAccessService;
import top.egon.mario.rag.service.RagRetrievalService;
import top.egon.mario.rag.service.RagVectorService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Default retrieval service with user-level knowledge base filtering.
 */
@Service
@RequiredArgsConstructor
public class RagRetrievalServiceImpl implements RagRetrievalService {

    private final RagProperties properties;
    private final RagAccessService accessService;
    private final RagVectorService vectorService;

    @Override
    @Transactional(readOnly = true)
    public RetrievalSearchResponse search(RetrievalSearchRequest request, RbacPrincipal principal) {
        long startedAt = System.currentTimeMillis();
        List<SourceReferenceResponse> sources = searchSources(
                request.query(),
                request.knowledgeBaseIds(),
                request.topK(),
                request.similarityThreshold(),
                principal
        );
        return new RetrievalSearchResponse(request.query(), sources, System.currentTimeMillis() - startedAt);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SourceReferenceResponse> searchSources(String query, List<Long> knowledgeBaseIds, Integer topK,
                                                       BigDecimal threshold, RbacPrincipal principal) {
        Set<Long> readableIds = accessService.readableKnowledgeBaseIds(principal, knowledgeBaseIds);
        if (readableIds.isEmpty()) {
            return List.of();
        }
        int actualTopK = topK == null ? properties.retrieval().defaultTopK() : topK;
        BigDecimal actualThreshold = threshold == null ? properties.retrieval().defaultSimilarityThreshold() : threshold;
        return vectorService.search(query, readableIds, actualTopK, actualThreshold);
    }

}
