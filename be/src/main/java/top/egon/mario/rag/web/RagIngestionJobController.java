package top.egon.mario.rag.web;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.rag.dto.response.RagIngestionJobResponse;
import top.egon.mario.rag.service.RagIngestionJobService;
import top.egon.mario.rbac.po.enums.ApiMatcherType;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;
import top.egon.mario.rbac.service.resource.annotation.RbacApi;
import top.egon.mario.rbac.service.security.RbacPrincipal;

/**
 * Management endpoints for RAG ingestion jobs.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rag/ingestion-jobs")
public class RagIngestionJobController extends ReactiveRagSupport {

    private final RagIngestionJobService ingestionJobService;

    @RbacApi(appCode = "rag", code = "api:rag:ingestion-job:collection", name = "RAG 入库任务集合",
            method = "ANY", pattern = "/api/rag/ingestion-jobs", risk = ApiRiskLevel.MEDIUM)
    @GetMapping
    public Mono<ApiResponse<PageResult<RagIngestionJobResponse>>> page(@RequestParam(required = false) Long knowledgeBaseId,
                                                                       @RequestParam(defaultValue = "1") int page,
                                                                       @RequestParam(defaultValue = "20") int size,
                                                                       @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> pageResult(ingestionJobService.page(knowledgeBaseId, PageRequest.of(Math.max(page - 1, 0), size, Sort.by("id").descending()), principal)));
    }

    @RbacApi(appCode = "rag", code = "api:rag:ingestion-job:*", name = "RAG 入库任务管理",
            method = "ANY", pattern = "/api/rag/ingestion-jobs/**", matcher = ApiMatcherType.ANT, risk = ApiRiskLevel.MEDIUM)
    @PostMapping("/{id}/retry")
    public Mono<ApiResponse<RagIngestionJobResponse>> retry(@PathVariable Long id,
                                                            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> ingestionJobService.retry(id, principal));
    }

    @PostMapping("/{id}/cancel")
    public Mono<ApiResponse<Void>> cancel(@PathVariable Long id,
                                          @AuthenticationPrincipal RbacPrincipal principal) {
        return blockingVoid(() -> ingestionJobService.cancel(id, principal));
    }

}
