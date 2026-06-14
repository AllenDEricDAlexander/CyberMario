package top.egon.mario.rag.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.rag.dto.request.RetrievalSearchRequest;
import top.egon.mario.rag.dto.response.RagRetrievalTraceResponse;
import top.egon.mario.rag.dto.response.RetrievalSearchResponse;
import top.egon.mario.rag.service.RagRetrievalService;
import top.egon.mario.rag.service.RagRetrievalTraceService;
import top.egon.mario.rbac.po.enums.ApiMatcherType;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;
import top.egon.mario.rbac.service.resource.annotation.RbacApi;
import top.egon.mario.rbac.service.security.RbacPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Retrieval-only endpoints for RAG debugging.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rag/retrieval")
@Validated
public class RagRetrievalController extends ReactiveRagSupport {

    private final RagRetrievalService retrievalService;
    private final RagRetrievalTraceService traceService;

    @RbacApi(appCode = "rag", code = "api:rag:retrieval:search", name = "RAG 检索调试", risk = ApiRiskLevel.MEDIUM)
    @PostMapping("/search")
    public Mono<ApiResponse<RetrievalSearchResponse>> search(@Valid @RequestBody RetrievalSearchRequest request,
                                                             @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> retrievalService.search(request, principal));
    }

    @RbacApi(appCode = "rag", code = "api:rag:retrieval:trace", name = "RAG 检索追踪详情",
            method = "ANY", pattern = "/api/rag/retrieval/traces/**", matcher = ApiMatcherType.ANT, risk = ApiRiskLevel.MEDIUM)
    @GetMapping("/traces/{traceId}")
    public Mono<ApiResponse<RagRetrievalTraceResponse>> trace(@PathVariable String traceId,
                                                              @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> traceService.detail(traceId, principal));
    }

}
