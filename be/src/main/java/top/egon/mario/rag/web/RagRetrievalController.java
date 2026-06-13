package top.egon.mario.rag.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.rag.dto.request.RetrievalSearchRequest;
import top.egon.mario.rag.dto.response.RetrievalSearchResponse;
import top.egon.mario.rag.service.RagRetrievalService;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;
import top.egon.mario.rbac.service.resource.annotation.RbacApi;
import top.egon.mario.rbac.service.security.RbacPrincipal;

/**
 * Retrieval-only endpoints for RAG debugging.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rag/retrieval")
public class RagRetrievalController extends ReactiveRagSupport {

    private final RagRetrievalService retrievalService;

    @RbacApi(appCode = "rag", code = "api:rag:retrieval:search", name = "RAG 检索调试", risk = ApiRiskLevel.MEDIUM)
    @PostMapping("/search")
    public Mono<ApiResponse<RetrievalSearchResponse>> search(@Valid @RequestBody RetrievalSearchRequest request,
                                                             @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> retrievalService.search(request, principal));
    }

}
