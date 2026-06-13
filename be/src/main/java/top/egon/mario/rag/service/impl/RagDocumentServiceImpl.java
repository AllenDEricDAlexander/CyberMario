package top.egon.mario.rag.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rag.converter.RagDtoConverter;
import top.egon.mario.rag.dto.request.ImportTextDocumentRequest;
import top.egon.mario.rag.dto.response.RagChunkResponse;
import top.egon.mario.rag.dto.response.RagDocumentResponse;
import top.egon.mario.rag.dto.response.UploadDocumentResponse;
import top.egon.mario.rag.po.RagDocumentChunkPo;
import top.egon.mario.rag.po.RagFileObjectPo;
import top.egon.mario.rag.po.RagIngestionJobPo;
import top.egon.mario.rag.po.RagUserDocumentPo;
import top.egon.mario.rag.po.enums.RagAccessLevel;
import top.egon.mario.rag.po.enums.RagDocumentSourceType;
import top.egon.mario.rag.po.enums.RagDocumentStatus;
import top.egon.mario.rag.po.enums.RagFileType;
import top.egon.mario.rag.po.enums.RagIngestionJobStatus;
import top.egon.mario.rag.po.enums.RagIngestionStep;
import top.egon.mario.rag.repository.RagDocumentChunkRepository;
import top.egon.mario.rag.repository.RagFileObjectRepository;
import top.egon.mario.rag.repository.RagIngestionJobRepository;
import top.egon.mario.rag.repository.RagUserDocumentRepository;
import top.egon.mario.rag.service.RagAccessService;
import top.egon.mario.rag.service.RagDocumentService;
import top.egon.mario.rag.service.RagException;
import top.egon.mario.rag.service.RagFileDigest;
import top.egon.mario.rag.service.RagIngestionService;
import top.egon.mario.rag.service.RagVectorService;
import top.egon.mario.rag.storage.RagFileStorage;
import top.egon.mario.rag.storage.RagStoredFile;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Default document service with global file de-duplication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagDocumentServiceImpl implements RagDocumentService {

    private final RagAccessService accessService;
    private final RagFileStorage fileStorage;
    private final RagFileObjectRepository fileObjectRepository;
    private final RagUserDocumentRepository documentRepository;
    private final RagDocumentChunkRepository chunkRepository;
    private final RagIngestionJobRepository jobRepository;
    private final RagIngestionService ingestionService;
    private final RagVectorService vectorService;
    private final RagDtoConverter dtoConverter;

    @Override
    public Mono<UploadDocumentResponse> upload(Long knowledgeBaseId, Flux<FilePart> files, boolean parseImmediately, RbacPrincipal principal) {
        accessService.requireAccess(principal, knowledgeBaseId, RagAccessLevel.WRITE);
        return files.concatMap(file -> saveFilePart(knowledgeBaseId, file, parseImmediately, principal))
                .collectList()
                .map(this::toUploadResponse);
    }

    @Override
    @Transactional
    public RagDocumentResponse importText(ImportTextDocumentRequest request, RbacPrincipal principal) {
        accessService.requireAccess(principal, request.knowledgeBaseId(), RagAccessLevel.WRITE);
        try {
            Path tempFile = Files.createTempFile("rag-text-", ".txt");
            Files.writeString(tempFile, request.content(), StandardCharsets.UTF_8);
            RagDocumentWithJob result = saveDocumentFromTempFile(
                    request.knowledgeBaseId(),
                    tempFile,
                    request.title(),
                    "text/plain",
                    RagFileType.TEXT,
                    RagDocumentSourceType.TEXT,
                    Boolean.TRUE.equals(request.parseImmediately()),
                    principal
            );
            return result.document();
        } catch (Exception e) {
            throw new RagException("RAG_TEXT_IMPORT_FAILED", "text import failed");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RagDocumentResponse> page(Long knowledgeBaseId, Pageable pageable, RbacPrincipal principal) {
        Set<Long> readableIds = accessService.readableKnowledgeBaseIds(principal, knowledgeBaseId == null ? List.of() : List.of(knowledgeBaseId));
        if (readableIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return documentRepository.findAll((root, query, cb) -> cb.and(
                        cb.isFalse(root.get("deleted")),
                        root.get("knowledgeBaseId").in(readableIds)
                ), pageable)
                .map(dtoConverter::toDocumentResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public RagDocumentResponse detail(Long id, RbacPrincipal principal) {
        RagUserDocumentPo document = getDocument(id);
        accessService.requireAccess(principal, document.getKnowledgeBaseId(), RagAccessLevel.READ);
        return dtoConverter.toDocumentResponse(document);
    }

    @Override
    @Transactional
    public void delete(Long id, RbacPrincipal principal) {
        RagUserDocumentPo document = getDocument(id);
        accessService.requireAccess(principal, document.getKnowledgeBaseId(), RagAccessLevel.WRITE);
        List<RagDocumentChunkPo> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(id);
        vectorService.deleteChunks(chunks);
        document.setStatus(RagDocumentStatus.DELETED);
        document.setDeleted(true);
        documentRepository.save(document);
        chunks.forEach(chunk -> {
            chunk.setDeleted(true);
            chunkRepository.save(chunk);
        });
    }

    @Override
    @Transactional
    public RagDocumentResponse reindex(Long id, RbacPrincipal principal) {
        RagUserDocumentPo document = getDocument(id);
        accessService.requireAccess(principal, document.getKnowledgeBaseId(), RagAccessLevel.WRITE);
        RagIngestionJobPo job = createJob(document);
        ingestionService.ingest(job.getId());
        return dtoConverter.toDocumentResponse(getDocument(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RagChunkResponse> chunks(Long documentId, Pageable pageable, RbacPrincipal principal) {
        RagUserDocumentPo document = getDocument(documentId);
        accessService.requireAccess(principal, document.getKnowledgeBaseId(), RagAccessLevel.READ);
        return chunkRepository.findByDocumentIdAndDeletedFalse(documentId, pageable)
                .map(dtoConverter::toChunkResponse);
    }

    @Override
    @Transactional
    public void updateChunkEnabled(Long chunkId, boolean enabled, RbacPrincipal principal) {
        RagDocumentChunkPo chunk = chunkRepository.findByIdAndDeletedFalse(chunkId)
                .orElseThrow(() -> new RagException("RAG_CHUNK_NOT_FOUND", "chunk not found"));
        accessService.requireAccess(principal, chunk.getKnowledgeBaseId(), RagAccessLevel.WRITE);
        chunk.setEnabled(enabled);
        chunkRepository.save(chunk);
    }

    private Mono<RagDocumentWithJob> saveFilePart(Long knowledgeBaseId, FilePart file, boolean parseImmediately, RbacPrincipal principal) {
        return Mono.usingWhen(
                        Mono.fromCallable(() -> Files.createTempFile("rag-upload-", "-" + file.filename())),
                        tempFile -> file.transferTo(tempFile)
                                .then(Mono.fromCallable(() -> saveDocumentFromTempFile(
                                        knowledgeBaseId,
                                        tempFile,
                                        file.filename(),
                                        file.headers().getContentType() == null ? null : file.headers().getContentType().toString(),
                                        RagFileType.fromFilename(file.filename()),
                                        RagDocumentSourceType.UPLOAD,
                                        parseImmediately,
                                        principal
                                )).subscribeOn(Schedulers.boundedElastic())),
                        tempFile -> Mono.fromRunnable(() -> deleteTemporaryFile(tempFile))
                )
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional
    protected RagDocumentWithJob saveDocumentFromTempFile(Long knowledgeBaseId,
                                                          Path tempFile,
                                                          String filename,
                                                          String contentType,
                                                          RagFileType fileType,
                                                          RagDocumentSourceType sourceType,
                                                          boolean parseImmediately,
                                                          RbacPrincipal principal) throws Exception {
        String sha256 = RagFileDigest.sha256(tempFile);
        RagFileObjectPo fileObject = fileObjectRepository.findBySha256AndDeletedFalse(sha256)
                .orElseGet(() -> createFileObject(tempFile, sha256, filename, contentType, fileType));
        if (!sha256.equals(fileObject.getSha256())) {
            throw new RagException("RAG_FILE_HASH_MISMATCH", "file hash mismatch");
        }
        RagUserDocumentPo document = createDocument(knowledgeBaseId, fileObject, filename, sourceType, principal);
        RagIngestionJobPo job = createJob(document);
        if (parseImmediately) {
            ingestionService.ingest(job.getId());
        }
        LogUtil.info(log).log("rag document linked, documentId={}, fileObjectId={}, sha256={}",
                document.getId(), fileObject.getId(), sha256);
        return new RagDocumentWithJob(dtoConverter.toDocumentResponse(documentRepository.findById(document.getId()).orElse(document)), job.getId());
    }

    private RagFileObjectPo createFileObject(Path tempFile, String sha256, String filename, String contentType, RagFileType fileType) {
        try {
            RagStoredFile storedFile = fileStorage.save(tempFile, sha256, filename);
            RagFileObjectPo fileObject = new RagFileObjectPo();
            fileObject.setSha256(sha256);
            fileObject.setStorageType(storedFile.storageType());
            fileObject.setBucket(storedFile.bucket());
            fileObject.setStorageKey(storedFile.storageKey());
            fileObject.setOriginalFilename(filename);
            fileObject.setContentType(contentType);
            fileObject.setFileType(fileType);
            fileObject.setFileSize(storedFile.fileSize());
            return fileObjectRepository.save(fileObject);
        } catch (Exception e) {
            throw new RagException("RAG_FILE_SAVE_FAILED", "file save failed");
        }
    }

    private RagUserDocumentPo createDocument(Long knowledgeBaseId, RagFileObjectPo fileObject, String displayName,
                                             RagDocumentSourceType sourceType, RbacPrincipal principal) {
        RagUserDocumentPo document = new RagUserDocumentPo();
        document.setUserId(principal == null ? 0L : principal.userId());
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setFileObjectId(fileObject.getId());
        document.setDisplayName(displayName);
        document.setSourceType(sourceType);
        document.setFileType(fileObject.getFileType());
        document.setContentHash(fileObject.getSha256());
        document.setStatus(RagDocumentStatus.UPLOADED);
        return documentRepository.save(document);
    }

    private RagIngestionJobPo createJob(RagUserDocumentPo document) {
        RagIngestionJobPo job = new RagIngestionJobPo();
        job.setDocumentId(document.getId());
        job.setKnowledgeBaseId(document.getKnowledgeBaseId());
        job.setFileObjectId(document.getFileObjectId());
        job.setStatus(RagIngestionJobStatus.PENDING);
        job.setCurrentStep(RagIngestionStep.UPLOAD);
        return jobRepository.save(job);
    }

    private UploadDocumentResponse toUploadResponse(List<RagDocumentWithJob> results) {
        return new UploadDocumentResponse(
                results.stream().map(RagDocumentWithJob::document).toList(),
                results.stream().map(RagDocumentWithJob::jobId).toList()
        );
    }

    private RagUserDocumentPo getDocument(Long id) {
        return documentRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RagException("RAG_DOCUMENT_NOT_FOUND", "document not found"));
    }

    private void deleteTemporaryFile(Path tempFile) {
        try {
            Files.deleteIfExists(tempFile);
        } catch (Exception ignored) {
            // Temporary file cleanup must not fail the upload response.
        }
    }

    private record RagDocumentWithJob(RagDocumentResponse document, Long jobId) {
    }

}
