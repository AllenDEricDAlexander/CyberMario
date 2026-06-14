package top.egon.mario.rag.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.rag.dto.request.ImportTextDocumentRequest;
import top.egon.mario.rag.dto.request.UpdateChunkEnabledRequest;
import top.egon.mario.rag.dto.response.RagChunkResponse;
import top.egon.mario.rag.dto.response.RagDocumentResponse;
import top.egon.mario.rag.dto.response.UploadDocumentResponse;
import top.egon.mario.rag.service.RagDocumentService;
import top.egon.mario.rbac.po.enums.ApiMatcherType;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;
import top.egon.mario.rbac.service.resource.annotation.RbacApi;
import top.egon.mario.rbac.service.security.RbacPrincipal;

/**
 * Management endpoints for RAG documents and chunks.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rag")
@Validated
public class RagDocumentController extends ReactiveRagSupport {

    private final RagDocumentService documentService;

    @RbacApi(appCode = "rag", code = "api:rag:document:collection", name = "RAG 文档集合",
            method = "ANY", pattern = "/api/rag/documents", risk = ApiRiskLevel.HIGH)
    @GetMapping("/documents")
    public Mono<ApiResponse<PageResult<RagDocumentResponse>>> page(@RequestParam(required = false) @Min(1) Long knowledgeBaseId,
                                                                   @RequestParam(defaultValue = "1") @Min(1) int page,
                                                                   @RequestParam(defaultValue = "20") @Min(1) int size,
                                                                   @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> pageResult(documentService.page(knowledgeBaseId, PageRequest.of(Math.max(page - 1, 0), size, Sort.by("id").descending()), principal)));
    }

    @RbacApi(appCode = "rag", code = "api:rag:document:*", name = "RAG 文档管理",
            method = "ANY", pattern = "/api/rag/documents/**", matcher = ApiMatcherType.ANT, risk = ApiRiskLevel.HIGH)
    @PostMapping(path = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ApiResponse<UploadDocumentResponse>> upload(@RequestParam @Min(1) Long knowledgeBaseId,
                                                            @RequestParam(defaultValue = "true") boolean parseImmediately,
                                                            @RequestPart("files") Flux<FilePart> files,
                                                            @AuthenticationPrincipal RbacPrincipal principal) {
        return documentService.upload(knowledgeBaseId, files, parseImmediately, principal)
                .map(ApiResponse::ok);
    }

    @PostMapping("/documents/import-text")
    public Mono<ApiResponse<RagDocumentResponse>> importText(@Valid @RequestBody ImportTextDocumentRequest request,
                                                             @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> documentService.importText(request, principal));
    }

    @GetMapping("/documents/{id}")
    public Mono<ApiResponse<RagDocumentResponse>> detail(@PathVariable @Min(1) Long id,
                                                         @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> documentService.detail(id, principal));
    }

    @DeleteMapping("/documents/{id}")
    public Mono<ApiResponse<Void>> delete(@PathVariable @Min(1) Long id,
                                          @AuthenticationPrincipal RbacPrincipal principal) {
        return blockingVoid(() -> documentService.delete(id, principal));
    }

    @PostMapping("/documents/{id}/reindex")
    public Mono<ApiResponse<RagDocumentResponse>> reindex(@PathVariable @Min(1) Long id,
                                                          @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> documentService.reindex(id, principal));
    }

    @GetMapping("/documents/{id}/chunks")
    public Mono<ApiResponse<PageResult<RagChunkResponse>>> chunks(@PathVariable @Min(1) Long id,
                                                                  @RequestParam(defaultValue = "1") @Min(1) int page,
                                                                  @RequestParam(defaultValue = "20") @Min(1) int size,
                                                                  @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> pageResult(documentService.chunks(id, PageRequest.of(Math.max(page - 1, 0), size, Sort.by("chunkIndex").ascending()), principal)));
    }

    @RbacApi(appCode = "rag", code = "api:rag:chunk:*", name = "RAG 切片管理",
            method = "ANY", pattern = "/api/rag/chunks/**", matcher = ApiMatcherType.ANT, risk = ApiRiskLevel.HIGH)
    @PatchMapping("/chunks/{id}/enabled")
    public Mono<ApiResponse<Void>> updateChunkEnabled(@PathVariable @Min(1) Long id,
                                                      @Valid @RequestBody UpdateChunkEnabledRequest request,
                                                      @AuthenticationPrincipal RbacPrincipal principal) {
        return blockingVoid(() -> documentService.updateChunkEnabled(id, request.enabled(), principal));
    }

}
