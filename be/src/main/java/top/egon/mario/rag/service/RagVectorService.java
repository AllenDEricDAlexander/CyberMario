package top.egon.mario.rag.service;

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
    void indexChunks(RagKnowledgeBasePo knowledgeBase, RagUserDocumentPo document, List<RagDocumentChunkPo> chunks);

    /**
     * Removes indexed vectors for chunks that are being replaced or deleted.
     */
    void deleteChunks(List<RagDocumentChunkPo> chunks);

    /**
     * Retrieves source chunks visible to the current user.
     */
    List<SourceReferenceResponse> search(String query, Collection<Long> knowledgeBaseIds, int topK, BigDecimal threshold);

}
