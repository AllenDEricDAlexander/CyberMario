package top.egon.mario.rag.service;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import top.egon.mario.rag.dto.response.SourceReferenceResponse;
import top.egon.mario.rag.po.RagDocumentChunkPo;
import top.egon.mario.rag.po.RagKnowledgeBasePo;
import top.egon.mario.rag.po.RagUserDocumentPo;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * Boundary for indexing and searching RAG chunks in a vector backend.
 */
public interface RagVectorService {

    /**
     * Indexes chunks for semantic retrieval.
     */
    void indexChunks(@NotNull RagKnowledgeBasePo knowledgeBase, @NotNull RagUserDocumentPo document, @NotNull List<RagDocumentChunkPo> chunks);

    /**
     * Removes indexed vectors for chunks that are being replaced or deleted.
     */
    void deleteChunks(@NotNull List<RagDocumentChunkPo> chunks);

    /**
     * Retrieves source chunks visible to the current user.
     */
    List<SourceReferenceResponse> search(@NotBlank String query, @NotNull Collection<Long> knowledgeBaseIds,
                                         @Min(1) @Max(20) int topK, @NotNull BigDecimal threshold);

}
