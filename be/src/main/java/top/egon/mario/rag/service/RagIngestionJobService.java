package top.egon.mario.rag.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import top.egon.mario.rag.dto.response.RagIngestionJobResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

/**
 * Application service for listing and controlling ingestion jobs.
 */
public interface RagIngestionJobService {

    Page<RagIngestionJobResponse> page(Long knowledgeBaseId, Pageable pageable, RbacPrincipal principal);

    RagIngestionJobResponse retry(Long id, RbacPrincipal principal);

    void cancel(Long id, RbacPrincipal principal);

}
