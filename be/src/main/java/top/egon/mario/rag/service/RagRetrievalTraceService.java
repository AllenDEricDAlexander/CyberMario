package top.egon.mario.rag.service;

import top.egon.mario.rag.dto.response.RagRetrievalTraceResponse;
import top.egon.mario.rag.dto.response.RetrievalSearchResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

/**
 * Stores and reads retrieval traces for debugging.
 */
public interface RagRetrievalTraceService {

    void save(String query, RetrievalSearchResponse response, RbacPrincipal principal);

    RagRetrievalTraceResponse detail(String traceId, RbacPrincipal principal);

}
