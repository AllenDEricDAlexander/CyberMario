package top.egon.mario.rag.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.rag.config.RagProperties;
import top.egon.mario.rag.dto.request.RetrievalSearchRequest;
import top.egon.mario.rag.dto.response.RagSearchMode;
import top.egon.mario.rag.dto.response.RetrievalSearchResponse;
import top.egon.mario.rag.dto.response.RetrievalSearchResponse.RetrievalStages;
import top.egon.mario.rag.dto.response.SourceReferenceResponse;
import top.egon.mario.rag.po.RagKnowledgeBasePo;
import top.egon.mario.rag.po.enums.RagKnowledgeBaseStatus;
import top.egon.mario.rag.repository.RagKnowledgeBaseRepository;
import top.egon.mario.rag.service.RagAccessService;
import top.egon.mario.rag.service.RagKeywordSearchService;
import top.egon.mario.rag.service.RagRerankService;
import top.egon.mario.rag.service.RagRetrievalService;
import top.egon.mario.rag.service.RagRetrievalTraceService;
import top.egon.mario.rag.service.RagVectorService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Default retrieval service with user-level knowledge base filtering and hybrid ranking.
 */
@Service
@RequiredArgsConstructor
@Validated
public class RagRetrievalServiceImpl implements RagRetrievalService {

    private static final double RRF_K = 60D;

    private final RagProperties properties;
    private final RagAccessService accessService;
    private final RagVectorService vectorService;
    private final RagKeywordSearchService keywordSearchService;
    private final RagRerankService rerankService;
    private final RagRetrievalTraceService traceService;
    private final RagKnowledgeBaseRepository knowledgeBaseRepository;

    @Override
    @Transactional(readOnly = true)
    public RetrievalSearchResponse search(RetrievalSearchRequest request, RbacPrincipal principal) {
        long startedAt = System.currentTimeMillis();
        RetrievalResult result = retrieve(request.query(), request.knowledgeBaseIds(), request.topK(), request.candidateTopK(),
                request.similarityThreshold(), request.searchMode(), request.rerankEnabled(), principal);
        RetrievalSearchResponse response = new RetrievalSearchResponse(
                request.query(),
                result.searchMode(),
                result.traceId(),
                result.results(),
                result.stages(),
                System.currentTimeMillis() - startedAt
        );
        traceService.save(request.query(), response, principal);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SourceReferenceResponse> searchSources(String query, List<Long> knowledgeBaseIds, Integer topK,
                                                       BigDecimal threshold, RbacPrincipal principal) {
        return searchSources(query, knowledgeBaseIds, topK, null, threshold, null, null, principal);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SourceReferenceResponse> searchSources(String query, List<Long> knowledgeBaseIds, Integer topK,
                                                       Integer candidateTopK, BigDecimal threshold, RagSearchMode searchMode,
                                                       Boolean rerankEnabled, RbacPrincipal principal) {
        return retrieve(query, knowledgeBaseIds, topK, candidateTopK, threshold, searchMode, rerankEnabled, principal).results();
    }

    private RetrievalResult retrieve(String query, List<Long> knowledgeBaseIds, Integer topK, Integer candidateTopK,
                                     BigDecimal threshold, RagSearchMode requestedMode, Boolean requestedRerank,
                                     RbacPrincipal principal) {
        Set<Long> readableIds = accessService.readableKnowledgeBaseIds(principal, knowledgeBaseIds);
        if (readableIds.isEmpty()) {
            RetrievalStages stages = new RetrievalStages(List.of(), List.of(), List.of(), List.of());
            return new RetrievalResult(actualSearchMode(requestedMode, requestedRerank), traceId(), List.of(), stages);
        }
        List<Long> readableList = readableIds.stream().sorted().toList();
        RetrievalDefaults defaults = defaults(readableList);
        int actualTopK = topK == null ? defaults.topK() : topK;
        int actualCandidateTopK = candidateTopK == null ? defaults.candidateTopK() : candidateTopK;
        BigDecimal actualThreshold = threshold == null ? defaults.similarityThreshold() : threshold;
        RagSearchMode actualMode = actualSearchMode(requestedMode, requestedRerank, defaults);

        List<SourceReferenceResponse> vector = needsVector(actualMode)
                ? vectorService.search(query, readableList, actualCandidateTopK, actualThreshold).stream()
                .map(source -> withScores(source, source.score(), null, null, null, "VECTOR"))
                .toList()
                : List.of();
        List<SourceReferenceResponse> keyword = needsKeyword(actualMode)
                ? keywordSearchService.search(query, readableList, actualCandidateTopK)
                : List.of();
        List<SourceReferenceResponse> fused = fuse(vector, keyword, actualCandidateTopK,
                defaults.vectorWeight(), defaults.keywordWeight());
        List<SourceReferenceResponse> reranked = actualMode == RagSearchMode.HYBRID_RERANK
                ? rerankService.rerank(query, fused, actualTopK)
                : List.of();
        List<SourceReferenceResponse> results = actualMode == RagSearchMode.VECTOR
                ? vector.stream().limit(actualTopK).toList()
                : actualMode == RagSearchMode.KEYWORD
                ? keyword.stream().limit(actualTopK).toList()
                : actualMode == RagSearchMode.HYBRID_RERANK
                ? reranked.stream().limit(actualTopK).toList()
                : fused.stream().limit(actualTopK).toList();
        RetrievalStages stages = new RetrievalStages(vector, keyword, fused, reranked);
        return new RetrievalResult(actualMode, traceId(), results, stages);
    }

    private RagSearchMode actualSearchMode(RagSearchMode requestedMode, Boolean requestedRerank) {
        return actualSearchMode(requestedMode, requestedRerank, globalDefaults());
    }

    private RagSearchMode actualSearchMode(RagSearchMode requestedMode, Boolean requestedRerank, RetrievalDefaults defaults) {
        RagSearchMode mode = requestedMode == null ? defaults.searchMode() : requestedMode;
        boolean rerank = requestedRerank == null ? defaults.rerankEnabled() : requestedRerank;
        if (mode == RagSearchMode.HYBRID_RERANK && !rerank) {
            return RagSearchMode.HYBRID;
        }
        if (mode == RagSearchMode.HYBRID && rerank) {
            return RagSearchMode.HYBRID_RERANK;
        }
        return mode;
    }

    private RetrievalDefaults defaults(List<Long> knowledgeBaseIds) {
        if (knowledgeBaseIds.size() != 1) {
            return globalDefaults();
        }
        return knowledgeBaseRepository
                .findByIdInAndDeletedFalseAndStatus(knowledgeBaseIds, RagKnowledgeBaseStatus.ENABLED).stream()
                .findFirst()
                .map(knowledgeBase -> new RetrievalDefaults(
                        knowledgeBase.getDefaultTopK(),
                        knowledgeBase.getCandidateTopK(),
                        knowledgeBase.getDefaultSimilarityThreshold(),
                        knowledgeBase.getDefaultSearchMode(),
                        knowledgeBase.isRerankEnabled(),
                        knowledgeBase.getVectorWeight(),
                        knowledgeBase.getKeywordWeight()
                ))
                .orElseGet(this::globalDefaults);
    }

    private RetrievalDefaults globalDefaults() {
        return new RetrievalDefaults(
                properties.retrieval().defaultTopK(),
                properties.retrieval().candidateTopK(),
                properties.retrieval().defaultSimilarityThreshold(),
                properties.retrieval().defaultSearchMode(),
                properties.retrieval().rerankEnabled(),
                properties.retrieval().vectorWeight(),
                properties.retrieval().keywordWeight()
        );
    }

    private boolean needsVector(RagSearchMode mode) {
        return mode == RagSearchMode.VECTOR || mode == RagSearchMode.HYBRID || mode == RagSearchMode.HYBRID_RERANK;
    }

    private boolean needsKeyword(RagSearchMode mode) {
        return mode == RagSearchMode.KEYWORD || mode == RagSearchMode.HYBRID || mode == RagSearchMode.HYBRID_RERANK;
    }

    private List<SourceReferenceResponse> fuse(List<SourceReferenceResponse> vector, List<SourceReferenceResponse> keyword,
                                               int topK, BigDecimal vectorWeight, BigDecimal keywordWeight) {
        Map<Long, FusionCandidate> candidates = new LinkedHashMap<>();
        addRanked(candidates, vector, true);
        addRanked(candidates, keyword, false);
        return candidates.values().stream()
                .map(candidate -> candidate.toSource(vectorWeight, keywordWeight))
                .sorted(Comparator.comparingDouble(SourceReferenceResponse::fusionScore).reversed())
                .limit(topK)
                .toList();
    }

    private void addRanked(Map<Long, FusionCandidate> candidates, List<SourceReferenceResponse> sources, boolean vectorStage) {
        for (int i = 0; i < sources.size(); i++) {
            SourceReferenceResponse source = sources.get(i);
            FusionCandidate candidate = candidates.computeIfAbsent(source.chunkId(), id -> new FusionCandidate(source));
            double score = 1D / (RRF_K + i + 1D);
            if (vectorStage) {
                candidate.vectorScore = source.score();
                candidate.vectorRankScore = score;
            } else {
                candidate.keywordScore = source.score();
                candidate.keywordRankScore = score;
            }
        }
    }

    private SourceReferenceResponse withScores(SourceReferenceResponse source, Double vectorScore, Double keywordScore,
                                               Double fusionScore, Double rerankScore, String matchedBy) {
        Map<String, Object> metadata = new HashMap<>(source.metadata() == null ? Map.of() : source.metadata());
        metadata.put("matched_by", matchedBy);
        return new SourceReferenceResponse(
                source.sourceId(),
                source.knowledgeBaseId(),
                source.knowledgeBaseName(),
                source.documentId(),
                source.documentName(),
                source.chunkId(),
                source.chunkIndex(),
                source.score(),
                vectorScore,
                keywordScore,
                fusionScore,
                rerankScore,
                matchedBy,
                source.content(),
                metadata
        );
    }

    private String traceId() {
        return UUID.randomUUID().toString();
    }

    private record RetrievalResult(
            RagSearchMode searchMode,
            String traceId,
            List<SourceReferenceResponse> results,
            RetrievalStages stages
    ) {
    }

    private record RetrievalDefaults(
            int topK,
            int candidateTopK,
            BigDecimal similarityThreshold,
            RagSearchMode searchMode,
            boolean rerankEnabled,
            BigDecimal vectorWeight,
            BigDecimal keywordWeight
    ) {
    }

    private final class FusionCandidate {
        private final SourceReferenceResponse source;
        private Double vectorScore;
        private Double keywordScore;
        private double vectorRankScore;
        private double keywordRankScore;

        private FusionCandidate(SourceReferenceResponse source) {
            this.source = source;
        }

        private SourceReferenceResponse toSource(BigDecimal vectorWeight, BigDecimal keywordWeight) {
            double fusionScore = vectorRankScore * vectorWeight.doubleValue()
                    + keywordRankScore * keywordWeight.doubleValue();
            List<String> matched = new ArrayList<>();
            if (vectorScore != null) {
                matched.add("VECTOR");
            }
            if (keywordScore != null) {
                matched.add("KEYWORD");
            }
            Map<String, Object> metadata = new HashMap<>(source.metadata() == null ? Map.of() : source.metadata());
            metadata.put("matched_by", String.join(",", matched));
            metadata.put("fusion_score", fusionScore);
            return new SourceReferenceResponse(
                    source.sourceId(),
                    source.knowledgeBaseId(),
                    source.knowledgeBaseName(),
                    source.documentId(),
                    source.documentName(),
                    source.chunkId(),
                    source.chunkIndex(),
                    fusionScore,
                    vectorScore,
                    keywordScore,
                    fusionScore,
                    null,
                    String.join(",", matched),
                    source.content(),
                    metadata
            );
        }
    }

}
