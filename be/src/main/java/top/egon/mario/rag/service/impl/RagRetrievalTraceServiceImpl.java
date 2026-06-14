package top.egon.mario.rag.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.rag.dto.response.RagRetrievalTraceResponse;
import top.egon.mario.rag.dto.response.RetrievalSearchResponse;
import top.egon.mario.rag.dto.response.SourceReferenceResponse;
import top.egon.mario.rag.po.RagRetrievalTraceItemPo;
import top.egon.mario.rag.po.RagRetrievalTracePo;
import top.egon.mario.rag.po.enums.RagRetrievalStage;
import top.egon.mario.rag.repository.RagRetrievalTraceItemRepository;
import top.egon.mario.rag.repository.RagRetrievalTraceRepository;
import top.egon.mario.rag.service.RagException;
import top.egon.mario.rag.service.RagRetrievalTraceService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Default retrieval trace storage service.
 */
@Service
@RequiredArgsConstructor
@Validated
public class RagRetrievalTraceServiceImpl implements RagRetrievalTraceService {

    private final RagRetrievalTraceRepository traceRepository;
    private final RagRetrievalTraceItemRepository traceItemRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(String query, RetrievalSearchResponse response, RbacPrincipal principal) {
        RagRetrievalTracePo trace = new RagRetrievalTracePo();
        trace.setTraceId(response.traceId());
        trace.setUserId(principal == null ? null : principal.userId());
        trace.setQueryText(query);
        trace.setKnowledgeBaseIds(response.results().stream()
                .map(SourceReferenceResponse::knowledgeBaseId)
                .distinct()
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse(""));
        trace.setSearchMode(response.searchMode());
        trace.setRerankEnabled(response.searchMode() != null && "HYBRID_RERANK".equals(response.searchMode().name()));
        trace.setResultCount(response.results().size());
        trace.setCostMs(response.costMs());
        traceRepository.save(trace);
        saveItems(response.traceId(), RagRetrievalStage.VECTOR, response.stages().vector());
        saveItems(response.traceId(), RagRetrievalStage.KEYWORD, response.stages().keyword());
        saveItems(response.traceId(), RagRetrievalStage.FUSED, response.stages().fused());
        saveItems(response.traceId(), RagRetrievalStage.RERANKED, response.stages().reranked());
        saveItems(response.traceId(), RagRetrievalStage.FINAL, response.results());
    }

    @Override
    @Transactional(readOnly = true)
    public RagRetrievalTraceResponse detail(String traceId, RbacPrincipal principal) {
        RagRetrievalTracePo trace = traceRepository.findByTraceIdAndDeletedFalse(traceId)
                .orElseThrow(() -> new RagException("RAG_TRACE_NOT_FOUND", "retrieval trace not found"));
        Map<RagRetrievalStage, List<SourceReferenceResponse>> items = new EnumMap<>(RagRetrievalStage.class);
        traceItemRepository.findByTraceIdAndDeletedFalseOrderByStageAscRankNoAsc(traceId).stream()
                .collect(java.util.stream.Collectors.groupingBy(RagRetrievalTraceItemPo::getStage))
                .forEach((stage, values) -> items.put(stage, values.stream().map(this::toSource).toList()));
        return new RagRetrievalTraceResponse(
                trace.getTraceId(),
                trace.getUserId(),
                trace.getQueryText(),
                trace.getSearchMode(),
                trace.isRerankEnabled(),
                trace.isDegraded(),
                trace.getDegradeReason(),
                trace.getCostMs(),
                items.getOrDefault(RagRetrievalStage.VECTOR, List.of()),
                items.getOrDefault(RagRetrievalStage.KEYWORD, List.of()),
                items.getOrDefault(RagRetrievalStage.FUSED, List.of()),
                items.getOrDefault(RagRetrievalStage.RERANKED, List.of()),
                trace.getCreatedAt()
        );
    }

    private void saveItems(String traceId, RagRetrievalStage stage, List<SourceReferenceResponse> sources) {
        for (int i = 0; i < sources.size(); i++) {
            SourceReferenceResponse source = sources.get(i);
            RagRetrievalTraceItemPo item = new RagRetrievalTraceItemPo();
            item.setTraceId(traceId);
            item.setStage(stage);
            item.setRankNo(i + 1);
            item.setKnowledgeBaseId(source.knowledgeBaseId());
            item.setDocumentId(source.documentId());
            item.setChunkId(source.chunkId());
            item.setChunkIndex(source.chunkIndex());
            item.setScore(source.score());
            item.setVectorScore(source.vectorScore());
            item.setKeywordScore(source.keywordScore());
            item.setFusionScore(source.fusionScore());
            item.setRerankScore(source.rerankScore());
            item.setMatchedBy(source.matchedBy());
            item.setContentPreview(preview(source.content()));
            item.setMetadataJson(metadataJson(source.metadata()));
            traceItemRepository.save(item);
        }
    }

    private SourceReferenceResponse toSource(RagRetrievalTraceItemPo item) {
        return new SourceReferenceResponse(
                String.valueOf(item.getChunkId()),
                item.getKnowledgeBaseId(),
                "",
                item.getDocumentId(),
                "",
                item.getChunkId(),
                item.getChunkIndex() == null ? 0 : item.getChunkIndex(),
                item.getScore() == null ? 0D : item.getScore(),
                item.getVectorScore(),
                item.getKeywordScore(),
                item.getFusionScore(),
                item.getRerankScore(),
                item.getMatchedBy(),
                item.getContentPreview(),
                Map.of()
        );
    }

    private String preview(String content) {
        if (content == null || content.length() <= 512) {
            return content;
        }
        return content.substring(0, 512);
    }

    private String metadataJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

}
