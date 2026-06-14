package top.egon.mario.rag.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import top.egon.mario.rag.dto.response.SourceReferenceResponse;

import java.util.List;

/**
 * Optional second-stage reranker for hybrid RAG candidates.
 */
public interface RagRerankService {

    List<SourceReferenceResponse> rerank(@NotBlank String query, @NotNull List<SourceReferenceResponse> candidates, int topK);

}
