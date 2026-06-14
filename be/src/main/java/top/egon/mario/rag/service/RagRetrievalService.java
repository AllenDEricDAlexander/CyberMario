package top.egon.mario.rag.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import top.egon.mario.rag.dto.request.RetrievalSearchRequest;
import top.egon.mario.rag.dto.response.RagSearchMode;
import top.egon.mario.rag.dto.response.RetrievalSearchResponse;
import top.egon.mario.rag.dto.response.SourceReferenceResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.math.BigDecimal;
import java.util.List;

/**
 * Application service for RAG source retrieval.
 */
public interface RagRetrievalService {

    RetrievalSearchResponse search(@Valid @NotNull RetrievalSearchRequest request, RbacPrincipal principal);

    List<SourceReferenceResponse> searchSources(@NotBlank String query, List<Long> knowledgeBaseIds, @Min(1) @Max(20) Integer topK,
                                                BigDecimal threshold, RbacPrincipal principal);

    List<SourceReferenceResponse> searchSources(@NotBlank String query, List<Long> knowledgeBaseIds, @Min(1) @Max(20) Integer topK,
                                                Integer candidateTopK, BigDecimal threshold, RagSearchMode searchMode,
                                                Boolean rerankEnabled, RbacPrincipal principal);

}
