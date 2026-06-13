package top.egon.mario.rag.service;

import top.egon.mario.rag.dto.request.RetrievalSearchRequest;
import top.egon.mario.rag.dto.response.RetrievalSearchResponse;
import top.egon.mario.rag.dto.response.SourceReferenceResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.math.BigDecimal;
import java.util.List;

/**
 * Application service for RAG source retrieval.
 */
public interface RagRetrievalService {

    RetrievalSearchResponse search(RetrievalSearchRequest request, RbacPrincipal principal);

    List<SourceReferenceResponse> searchSources(String query, List<Long> knowledgeBaseIds, Integer topK,
                                                BigDecimal threshold, RbacPrincipal principal);

}
