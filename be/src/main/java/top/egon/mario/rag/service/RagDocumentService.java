package top.egon.mario.rag.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.egon.mario.rag.dto.request.ImportTextDocumentRequest;
import top.egon.mario.rag.dto.response.RagChunkResponse;
import top.egon.mario.rag.dto.response.RagDocumentResponse;
import top.egon.mario.rag.dto.response.RagIngestionJobResponse;
import top.egon.mario.rag.dto.response.UploadDocumentResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.nio.file.Path;

/**
 * Application service for RAG document uploads, text imports and chunk management.
 */
public interface RagDocumentService {

    Mono<UploadDocumentResponse> upload(@NotNull Long knowledgeBaseId, @NotNull Flux<FilePart> files, boolean parseImmediately, RbacPrincipal principal);

    RagDocumentResponse importText(@Valid @NotNull ImportTextDocumentRequest request, RbacPrincipal principal);

    RagIngestionJobResponse importArxivPdf(@NotNull Long knowledgeBaseId, @NotNull Path pdfFile,
                                           @NotNull String displayName, RbacPrincipal principal);

    Page<RagDocumentResponse> page(Long knowledgeBaseId, @NotNull Pageable pageable, RbacPrincipal principal);

    RagDocumentResponse detail(@NotNull Long id, RbacPrincipal principal);

    void delete(@NotNull Long id, RbacPrincipal principal);

    RagDocumentResponse reindex(@NotNull Long id, RbacPrincipal principal);

    Page<RagChunkResponse> chunks(@NotNull Long documentId, @NotNull Pageable pageable, RbacPrincipal principal);

    void updateChunkEnabled(@NotNull Long chunkId, boolean enabled, RbacPrincipal principal);

}
