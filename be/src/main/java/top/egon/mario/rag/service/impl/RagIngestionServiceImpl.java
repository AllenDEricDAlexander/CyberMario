package top.egon.mario.rag.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rag.converter.RagDtoConverter;
import top.egon.mario.rag.dto.response.RagIngestionJobResponse;
import top.egon.mario.rag.po.RagDocumentChunkPo;
import top.egon.mario.rag.po.RagFileObjectPo;
import top.egon.mario.rag.po.RagIngestionJobPo;
import top.egon.mario.rag.po.RagKnowledgeBasePo;
import top.egon.mario.rag.po.RagUserDocumentPo;
import top.egon.mario.rag.po.enums.RagDocumentStatus;
import top.egon.mario.rag.po.enums.RagIngestionJobStatus;
import top.egon.mario.rag.po.enums.RagIngestionStep;
import top.egon.mario.rag.repository.RagDocumentChunkRepository;
import top.egon.mario.rag.repository.RagFileObjectRepository;
import top.egon.mario.rag.repository.RagIngestionJobRepository;
import top.egon.mario.rag.repository.RagKnowledgeBaseRepository;
import top.egon.mario.rag.repository.RagUserDocumentRepository;
import top.egon.mario.rag.service.RagDocumentParser;
import top.egon.mario.rag.service.RagException;
import top.egon.mario.rag.service.RagIngestionService;
import top.egon.mario.rag.service.RagTextChunker;
import top.egon.mario.rag.service.RagVectorService;
import top.egon.mario.rag.service.model.RagChunkCandidate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Synchronous ingestion service for the first RAG implementation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagIngestionServiceImpl implements RagIngestionService {

    private final RagIngestionJobRepository jobRepository;
    private final RagUserDocumentRepository documentRepository;
    private final RagFileObjectRepository fileObjectRepository;
    private final RagKnowledgeBaseRepository knowledgeBaseRepository;
    private final RagDocumentChunkRepository chunkRepository;
    private final RagDocumentParser documentParser;
    private final RagTextChunker textChunker;
    private final RagVectorService vectorService;
    private final RagDtoConverter dtoConverter;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public RagIngestionJobResponse ingest(Long jobId) {
        RagIngestionJobPo job = getJob(jobId);
        RagUserDocumentPo document = getDocument(job.getDocumentId());
        try {
            RagFileObjectPo fileObject = getFileObject(document.getFileObjectId());
            RagKnowledgeBasePo knowledgeBase = getKnowledgeBase(document.getKnowledgeBaseId());
            markRunning(job, document);
            String text = parse(job, document, fileObject);
            List<RagChunkCandidate> candidates = chunk(job, document, text);
            List<RagDocumentChunkPo> chunks = saveChunks(document, fileObject, candidates);
            index(job, document, knowledgeBase, chunks);
            markSuccess(job, document, chunks.size());
            LogUtil.info(log).log("rag ingestion completed, jobId={}, documentId={}, chunkCount={}",
                    job.getId(), document.getId(), chunks.size());
        } catch (Exception e) {
            markFailed(job, document, e);
        }
        return dtoConverter.toIngestionJobResponse(job);
    }

    private void markRunning(RagIngestionJobPo job, RagUserDocumentPo document) {
        job.setStatus(RagIngestionJobStatus.RUNNING);
        job.setCurrentStep(RagIngestionStep.PARSE);
        job.setProgress(10);
        job.setStartedAt(Instant.now());
        document.setStatus(RagDocumentStatus.PARSING);
    }

    private String parse(RagIngestionJobPo job, RagUserDocumentPo document, RagFileObjectPo fileObject) {
        String text = documentParser.parse(fileObject);
        document.setStatus(RagDocumentStatus.CHUNKING);
        job.setCurrentStep(RagIngestionStep.CHUNK);
        job.setProgress(35);
        return text;
    }

    private List<RagChunkCandidate> chunk(RagIngestionJobPo job, RagUserDocumentPo document, String text) {
        List<RagChunkCandidate> chunks = textChunker.split(text);
        if (chunks.isEmpty()) {
            throw new RagException("RAG_DOCUMENT_EMPTY", "document has no readable content");
        }
        document.setStatus(RagDocumentStatus.EMBEDDING);
        job.setCurrentStep(RagIngestionStep.EMBEDDING);
        job.setProgress(60);
        job.setChunkCount(chunks.size());
        return chunks;
    }

    private List<RagDocumentChunkPo> saveChunks(RagUserDocumentPo document, RagFileObjectPo fileObject, List<RagChunkCandidate> candidates) {
        List<RagDocumentChunkPo> existingChunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(document.getId());
        vectorService.deleteChunks(existingChunks);
        chunkRepository.deleteByDocumentId(document.getId());
        return chunkRepository.saveAll(candidates.stream()
                .map(candidate -> toChunk(document, fileObject, candidate))
                .toList());
    }

    private void index(RagIngestionJobPo job, RagUserDocumentPo document, RagKnowledgeBasePo knowledgeBase, List<RagDocumentChunkPo> chunks) {
        vectorService.indexChunks(knowledgeBase, document, chunks);
        document.setStatus(RagDocumentStatus.INDEXED);
        job.setCurrentStep(RagIngestionStep.INDEX);
        job.setProgress(90);
    }

    private void markSuccess(RagIngestionJobPo job, RagUserDocumentPo document, int chunkCount) {
        document.setChunkCount(chunkCount);
        document.setIndexedChunkCount(chunkCount);
        document.setErrorMessage(null);
        job.setStatus(RagIngestionJobStatus.SUCCESS);
        job.setCurrentStep(RagIngestionStep.DONE);
        job.setProgress(100);
        job.setSuccessCount(chunkCount);
        job.setFailedCount(0);
        job.setErrorMessage(null);
        job.setFinishedAt(Instant.now());
    }

    private void markFailed(RagIngestionJobPo job, RagUserDocumentPo document, Exception e) {
        document.setStatus(RagDocumentStatus.FAILED);
        document.setErrorMessage(e.getMessage());
        job.setStatus(RagIngestionJobStatus.FAILED);
        job.setErrorMessage(e.getMessage());
        job.setFailedCount(1);
        job.setFinishedAt(Instant.now());
        LogUtil.warn(log).log("rag ingestion failed, jobId={}, documentId={}, reason={}",
                job.getId(), document.getId(), e.getMessage());
    }

    private RagDocumentChunkPo toChunk(RagUserDocumentPo document, RagFileObjectPo fileObject, RagChunkCandidate candidate) {
        RagDocumentChunkPo chunk = new RagDocumentChunkPo();
        chunk.setDocumentId(document.getId());
        chunk.setKnowledgeBaseId(document.getKnowledgeBaseId());
        chunk.setFileObjectId(fileObject.getId());
        chunk.setChunkIndex(candidate.chunkIndex());
        chunk.setContent(candidate.content());
        chunk.setContentPreview(preview(candidate.content()));
        chunk.setTokenCount(candidate.tokenCount());
        chunk.setEnabled(true);
        chunk.setMetadataJson(metadataJson(document, candidate));
        return chunk;
    }

    private String preview(String content) {
        return content.length() <= 512 ? content : content.substring(0, 512);
    }

    private String metadataJson(RagUserDocumentPo document, RagChunkCandidate candidate) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "document_id", document.getId(),
                    "knowledge_base_id", document.getKnowledgeBaseId(),
                    "chunk_index", candidate.chunkIndex()
            ));
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private RagIngestionJobPo getJob(Long jobId) {
        return jobRepository.findByIdAndDeletedFalse(jobId)
                .orElseThrow(() -> new RagException("RAG_JOB_NOT_FOUND", "ingestion job not found"));
    }

    private RagUserDocumentPo getDocument(Long documentId) {
        return documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new RagException("RAG_DOCUMENT_NOT_FOUND", "document not found"));
    }

    private RagFileObjectPo getFileObject(Long fileObjectId) {
        return fileObjectRepository.findByIdAndDeletedFalse(fileObjectId)
                .orElseThrow(() -> new RagException("RAG_FILE_NOT_FOUND", "file object not found"));
    }

    private RagKnowledgeBasePo getKnowledgeBase(Long knowledgeBaseId) {
        return knowledgeBaseRepository.findByIdAndDeletedFalse(knowledgeBaseId)
                .orElseThrow(() -> new RagException("RAG_KB_NOT_FOUND", "knowledge base not found"));
    }

}
