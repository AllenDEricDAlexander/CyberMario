package top.egon.mario.rag.dto.response;

import java.util.Map;

/**
 * Source chunk returned from RAG retrieval.
 */
public record SourceReferenceResponse(
        String sourceId,
        Long knowledgeBaseId,
        String knowledgeBaseName,
        Long documentId,
        String documentName,
        Long chunkId,
        int chunkIndex,
        double score,
        Double vectorScore,
        Double keywordScore,
        Double fusionScore,
        Double rerankScore,
        String matchedBy,
        String content,
        Map<String, Object> metadata
) {
    public SourceReferenceResponse(
            String sourceId,
            Long knowledgeBaseId,
            String knowledgeBaseName,
            Long documentId,
            String documentName,
            Long chunkId,
            int chunkIndex,
            double score,
            String content,
            Map<String, Object> metadata
    ) {
        this(sourceId, knowledgeBaseId, knowledgeBaseName, documentId, documentName, chunkId, chunkIndex,
                score, null, null, null, null, null, content, metadata);
    }
}
