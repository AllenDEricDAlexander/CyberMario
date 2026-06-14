package top.egon.mario.rag.converter;

import org.springframework.stereotype.Component;
import top.egon.mario.rag.dto.response.KnowledgeBaseResponse;
import top.egon.mario.rag.dto.response.KnowledgeBaseUserResponse;
import top.egon.mario.rag.dto.response.RagChunkResponse;
import top.egon.mario.rag.dto.response.RagDocumentResponse;
import top.egon.mario.rag.dto.response.RagIngestionJobResponse;
import top.egon.mario.rag.po.RagDocumentChunkPo;
import top.egon.mario.rag.po.RagIngestionJobPo;
import top.egon.mario.rag.po.RagKnowledgeBasePo;
import top.egon.mario.rag.po.RagKnowledgeBaseUserPo;
import top.egon.mario.rag.po.RagUserDocumentPo;

/**
 * Converts RAG persistence objects to API DTOs.
 */
@Component
public class RagDtoConverter {

    public KnowledgeBaseResponse toKnowledgeBaseResponse(RagKnowledgeBasePo po) {
        return new KnowledgeBaseResponse(
                po.getId(),
                po.getCode(),
                po.getName(),
                po.getDescription(),
                po.getDefaultTopK(),
                po.getDefaultSimilarityThreshold(),
                po.getDefaultSearchMode(),
                po.isRerankEnabled(),
                po.getVectorWeight(),
                po.getKeywordWeight(),
                po.getCandidateTopK(),
                po.getContextTopK(),
                po.getChunkSize(),
                po.getChunkOverlap(),
                po.getStatus(),
                po.getCreatedAt(),
                po.getUpdatedAt()
        );
    }

    public KnowledgeBaseUserResponse toKnowledgeBaseUserResponse(RagKnowledgeBaseUserPo po) {
        return new KnowledgeBaseUserResponse(po.getId(), po.getKnowledgeBaseId(), po.getUserId(), po.getAccessLevel());
    }

    public RagDocumentResponse toDocumentResponse(RagUserDocumentPo po) {
        return new RagDocumentResponse(
                po.getId(),
                po.getUserId(),
                po.getKnowledgeBaseId(),
                po.getFileObjectId(),
                po.getDisplayName(),
                po.getSourceType(),
                po.getFileType(),
                po.getContentHash(),
                po.getStatus(),
                po.getChunkCount(),
                po.getIndexedChunkCount(),
                po.getErrorMessage(),
                po.getSourceUri(),
                po.getParserType(),
                po.getChunkStrategy(),
                po.getEmbeddingModel(),
                po.getEmbeddingDimension(),
                po.getIndexedAt(),
                po.getCreatedAt(),
                po.getUpdatedAt()
        );
    }

    public RagChunkResponse toChunkResponse(RagDocumentChunkPo po) {
        return new RagChunkResponse(
                po.getId(),
                po.getDocumentId(),
                po.getKnowledgeBaseId(),
                po.getChunkIndex(),
                po.getContent(),
                po.getContentPreview(),
                po.getTokenCount(),
                po.isEnabled(),
                po.getMetadataJson(),
                po.getContentHash(),
                po.getHeadingPath(),
                po.getStartOffset(),
                po.getEndOffset(),
                po.getCreatedAt()
        );
    }

    public RagIngestionJobResponse toIngestionJobResponse(RagIngestionJobPo po) {
        return new RagIngestionJobResponse(
                po.getId(),
                po.getDocumentId(),
                po.getKnowledgeBaseId(),
                po.getStatus(),
                po.getCurrentStep(),
                po.getProgress(),
                po.getChunkCount(),
                po.getSuccessCount(),
                po.getFailedCount(),
                po.getErrorMessage(),
                po.getStartedAt(),
                po.getFinishedAt(),
                po.getCreatedAt()
        );
    }

}
