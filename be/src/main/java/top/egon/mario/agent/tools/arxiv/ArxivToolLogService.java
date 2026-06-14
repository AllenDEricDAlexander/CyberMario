package top.egon.mario.agent.tools.arxiv;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.agent.tools.arxiv.dto.ArxivPaper;
import top.egon.mario.agent.tools.arxiv.po.ArxivToolLogPo;
import top.egon.mario.agent.tools.arxiv.po.enums.ArxivToolLogStatus;
import top.egon.mario.agent.tools.arxiv.repository.ArxivToolLogRepository;

import java.time.Instant;

/**
 * Persists arXiv tool search and import logs for super-admin review.
 */
@Service
@RequiredArgsConstructor
public class ArxivToolLogService {

    private static final int ERROR_MESSAGE_MAX_LENGTH = 1024;

    private final ArxivToolLogRepository repository;

    @Transactional
    public ArxivToolLogPo createSearchLog(String requestId, Long userId, String username, String query,
                                          int maxResults, boolean includeFullText, int resultCount,
                                          Long knowledgeBaseId) {
        Instant now = Instant.now();
        ArxivToolLogPo log = baseLog(requestId, userId, username, query, knowledgeBaseId);
        log.setMaxResults(maxResults);
        log.setIncludeFullText(includeFullText);
        log.setResultCount(resultCount);
        log.setStatus(ArxivToolLogStatus.SEARCHED);
        log.setStartedAt(now);
        log.setFinishedAt(now);
        log.setCreatedAt(now);
        return repository.save(log);
    }

    @Transactional
    public ArxivToolLogPo createImportLog(String requestId, Long userId, String username, String query,
                                          Long knowledgeBaseId, ArxivPaper paper) {
        Instant now = Instant.now();
        ArxivToolLogPo log = baseLog(requestId, userId, username, query, knowledgeBaseId);
        log.setEntryId(paper.entryId());
        log.setTitle(truncate(paper.title(), 512));
        log.setPdfUrl(truncate(paper.pdfUrl(), 512));
        log.setStatus(ArxivToolLogStatus.IMPORT_PENDING);
        log.setStartedAt(now);
        log.setCreatedAt(now);
        return repository.save(log);
    }

    @Transactional
    public void markImportRunning(ArxivToolLogPo log) {
        log.setStatus(ArxivToolLogStatus.IMPORT_RUNNING);
        repository.save(log);
    }

    @Transactional
    public void markImportSkipped(ArxivToolLogPo log, Long documentId, Long ragIngestionJobId) {
        log.setStatus(ArxivToolLogStatus.IMPORT_SKIPPED);
        log.setDocumentId(documentId);
        log.setRagIngestionJobId(ragIngestionJobId);
        log.setFinishedAt(Instant.now());
        repository.save(log);
    }

    @Transactional
    public void markImportSuccess(ArxivToolLogPo log, Long documentId, Long ragIngestionJobId) {
        log.setStatus(ArxivToolLogStatus.IMPORT_SUCCESS);
        log.setDocumentId(documentId);
        log.setRagIngestionJobId(ragIngestionJobId);
        log.setErrorMessage(null);
        log.setFinishedAt(Instant.now());
        repository.save(log);
    }

    @Transactional
    public void markImportFailed(ArxivToolLogPo log, String errorMessage) {
        log.setStatus(ArxivToolLogStatus.IMPORT_FAILED);
        log.setErrorMessage(truncate(errorMessage, ERROR_MESSAGE_MAX_LENGTH));
        log.setFinishedAt(Instant.now());
        repository.save(log);
    }

    private ArxivToolLogPo baseLog(String requestId, Long userId, String username, String query, Long knowledgeBaseId) {
        ArxivToolLogPo log = new ArxivToolLogPo();
        log.setRequestId(requestId);
        log.setRequestUserId(userId);
        log.setRequestUsername(username);
        log.setQuery(truncate(query, 1024));
        log.setKnowledgeBaseId(knowledgeBaseId);
        return log;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

}
