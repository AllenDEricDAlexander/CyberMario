package top.egon.mario.rag.service.impl;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import top.egon.mario.rag.dto.response.SourceReferenceResponse;
import top.egon.mario.rag.po.RagDocumentChunkPo;
import top.egon.mario.rag.po.RagKnowledgeBasePo;
import top.egon.mario.rag.po.RagUserDocumentPo;
import top.egon.mario.rag.repository.RagDocumentChunkRepository;
import top.egon.mario.rag.service.RagVectorService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Default vector service that uses Spring AI VectorStore when configured.
 */
@Service
public class DefaultRagVectorService implements RagVectorService {

    private final Optional<VectorStore> vectorStore;
    private final RagDocumentChunkRepository chunkRepository;

    public DefaultRagVectorService(Optional<VectorStore> vectorStore, RagDocumentChunkRepository chunkRepository) {
        this.vectorStore = vectorStore;
        this.chunkRepository = chunkRepository;
    }

    @Override
    public void indexChunks(RagKnowledgeBasePo knowledgeBase, RagUserDocumentPo document, List<RagDocumentChunkPo> chunks) {
        vectorStore.ifPresent(store -> {
            List<Document> documents = chunks.stream()
                    .map(chunk -> new Document(vectorId(chunk), chunk.getContent(), metadata(knowledgeBase, document, chunk)))
                    .toList();
            if (!documents.isEmpty()) {
                store.add(documents);
            }
        });
    }

    @Override
    public void deleteChunks(List<RagDocumentChunkPo> chunks) {
        vectorStore.ifPresent(store -> {
            List<String> vectorIds = chunks.stream()
                    .map(this::vectorId)
                    .toList();
            if (!vectorIds.isEmpty()) {
                store.delete(vectorIds);
            }
        });
    }

    @Override
    public List<SourceReferenceResponse> search(String query, Collection<Long> knowledgeBaseIds, int topK, BigDecimal threshold) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return List.of();
        }
        return vectorStore
                .map(store -> searchVectorStore(store, query, knowledgeBaseIds, topK, threshold))
                .orElseGet(() -> searchDatabase(query, knowledgeBaseIds, topK));
    }

    private List<SourceReferenceResponse> searchVectorStore(VectorStore store, String query, Collection<Long> knowledgeBaseIds,
                                                            int topK, BigDecimal threshold) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold.doubleValue())
                .filterExpression(filterExpression(knowledgeBaseIds))
                .build();
        return store.similaritySearch(request).stream()
                .map(this::toSource)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<SourceReferenceResponse> searchDatabase(String query, Collection<Long> knowledgeBaseIds, int topK) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase();
        if (normalizedQuery.isEmpty()) {
            return List.of();
        }
        return chunkRepository.findByKnowledgeBaseIdInAndEnabledTrueAndDeletedFalse(knowledgeBaseIds).stream()
                .map(chunk -> toSource(chunk, normalizedQuery))
                .filter(source -> source.score() > 0)
                .sorted(Comparator.comparingDouble(SourceReferenceResponse::score).reversed())
                .limit(topK)
                .toList();
    }

    private Map<String, Object> metadata(RagKnowledgeBasePo knowledgeBase, RagUserDocumentPo document, RagDocumentChunkPo chunk) {
        return Map.of(
                "knowledge_base_id", String.valueOf(knowledgeBase.getId()),
                "knowledge_base_name", knowledgeBase.getName(),
                "document_id", String.valueOf(document.getId()),
                "document_name", document.getDisplayName(),
                "chunk_id", String.valueOf(chunk.getId()),
                "chunk_index", chunk.getChunkIndex(),
                "enabled", true,
                "deleted", false
        );
    }

    private String vectorId(RagDocumentChunkPo chunk) {
        return UUID.nameUUIDFromBytes(("rag-chunk-" + chunk.getId()).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String filterExpression(Collection<Long> knowledgeBaseIds) {
        String ids = knowledgeBaseIds.stream()
                .map(id -> "'" + id + "'")
                .reduce((left, right) -> left + "," + right)
                .orElse("'0'");
        return "knowledge_base_id in [" + ids + "] && enabled == true && deleted == false";
    }

    private SourceReferenceResponse toSource(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        Long knowledgeBaseId = longMetadata(metadata, "knowledge_base_id");
        Long documentId = longMetadata(metadata, "document_id");
        Long chunkId = longMetadata(metadata, "chunk_id");
        if (knowledgeBaseId == null || documentId == null || chunkId == null) {
            return null;
        }
        return new SourceReferenceResponse(
                String.valueOf(chunkId),
                knowledgeBaseId,
                stringMetadata(metadata, "knowledge_base_name"),
                documentId,
                stringMetadata(metadata, "document_name"),
                chunkId,
                intMetadata(metadata, "chunk_index"),
                document.getScore() == null ? 0D : document.getScore(),
                document.getText(),
                metadata
        );
    }

    private SourceReferenceResponse toSource(RagDocumentChunkPo chunk, String query) {
        String content = chunk.getContent() == null ? "" : chunk.getContent();
        double score = content.toLowerCase().contains(query) ? 1D : 0D;
        return new SourceReferenceResponse(
                String.valueOf(chunk.getId()),
                chunk.getKnowledgeBaseId(),
                "",
                chunk.getDocumentId(),
                "",
                chunk.getId(),
                chunk.getChunkIndex(),
                score,
                content,
                Map.of("chunk_id", String.valueOf(chunk.getId()))
        );
    }

    private String stringMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private Long longMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }

    private int intMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? 0 : Integer.parseInt(String.valueOf(value));
    }

}
