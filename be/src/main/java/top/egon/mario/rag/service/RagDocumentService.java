package top.egon.mario.rag.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.egon.mario.rag.dto.request.ImportTextDocumentRequest;
import top.egon.mario.rag.dto.response.RagChunkResponse;
import top.egon.mario.rag.dto.response.RagDocumentResponse;
import top.egon.mario.rag.dto.response.UploadDocumentResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

/**
 * Application service for RAG document uploads, text imports and chunk management.
 */
public interface RagDocumentService {

    Mono<UploadDocumentResponse> upload(Long knowledgeBaseId, Flux<FilePart> files, boolean parseImmediately, RbacPrincipal principal);

    RagDocumentResponse importText(ImportTextDocumentRequest request, RbacPrincipal principal);

    Page<RagDocumentResponse> page(Long knowledgeBaseId, Pageable pageable, RbacPrincipal principal);

    RagDocumentResponse detail(Long id, RbacPrincipal principal);

    void delete(Long id, RbacPrincipal principal);

    RagDocumentResponse reindex(Long id, RbacPrincipal principal);

    Page<RagChunkResponse> chunks(Long documentId, Pageable pageable, RbacPrincipal principal);

    void updateChunkEnabled(Long chunkId, boolean enabled, RbacPrincipal principal);

}
