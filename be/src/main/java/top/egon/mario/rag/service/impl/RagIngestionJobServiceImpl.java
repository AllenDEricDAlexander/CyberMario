package top.egon.mario.rag.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.rag.converter.RagDtoConverter;
import top.egon.mario.rag.dto.response.RagIngestionJobResponse;
import top.egon.mario.rag.po.RagIngestionJobPo;
import top.egon.mario.rag.po.enums.RagAccessLevel;
import top.egon.mario.rag.po.enums.RagIngestionJobStatus;
import top.egon.mario.rag.repository.RagIngestionJobRepository;
import top.egon.mario.rag.service.RagAccessService;
import top.egon.mario.rag.service.RagException;
import top.egon.mario.rag.service.RagIngestionJobService;
import top.egon.mario.rag.service.RagIngestionService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Default ingestion job management service.
 */
@Service
@RequiredArgsConstructor
@Validated
public class RagIngestionJobServiceImpl implements RagIngestionJobService {

    private final RagIngestionJobRepository jobRepository;
    private final RagAccessService accessService;
    private final RagIngestionService ingestionService;
    private final RagDtoConverter dtoConverter;

    @Override
    @Transactional(readOnly = true)
    public Page<RagIngestionJobResponse> page(Long knowledgeBaseId, Pageable pageable, RbacPrincipal principal) {
        Set<Long> readableIds = accessService.readableKnowledgeBaseIds(principal, knowledgeBaseId == null ? List.of() : List.of(knowledgeBaseId));
        if (readableIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return jobRepository.findAll((root, query, cb) -> cb.and(
                        cb.isFalse(root.get("deleted")),
                        root.get("knowledgeBaseId").in(readableIds)
                ), pageable)
                .map(dtoConverter::toIngestionJobResponse);
    }

    @Override
    @Transactional
    public RagIngestionJobResponse retry(Long id, RbacPrincipal principal) {
        RagIngestionJobPo job = getJob(id);
        accessService.requireAccess(principal, job.getKnowledgeBaseId(), RagAccessLevel.WRITE);
        job.setStatus(RagIngestionJobStatus.PENDING);
        job.setProgress(0);
        job.setErrorMessage(null);
        job.setFinishedAt(null);
        jobRepository.save(job);
        return ingestionService.ingest(id);
    }

    @Override
    @Transactional
    public void cancel(Long id, RbacPrincipal principal) {
        RagIngestionJobPo job = getJob(id);
        accessService.requireAccess(principal, job.getKnowledgeBaseId(), RagAccessLevel.WRITE);
        job.setStatus(RagIngestionJobStatus.CANCELED);
        job.setFinishedAt(Instant.now());
        jobRepository.save(job);
    }

    private RagIngestionJobPo getJob(Long id) {
        return jobRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RagException("RAG_JOB_NOT_FOUND", "ingestion job not found"));
    }

}
