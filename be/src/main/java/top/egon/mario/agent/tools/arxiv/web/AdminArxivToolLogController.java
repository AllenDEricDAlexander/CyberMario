package top.egon.mario.agent.tools.arxiv.web;

import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import top.egon.mario.agent.tools.arxiv.ArxivToolLogQueryService;
import top.egon.mario.agent.tools.arxiv.dto.ArxivToolLogResponse;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.rbac.po.enums.ApiMatcherType;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;
import top.egon.mario.rbac.service.resource.annotation.RbacApi;
import top.egon.mario.rbac.service.resource.annotation.RbacApis;
import top.egon.mario.rbac.service.security.RbacPrincipal;

/**
 * Super-admin endpoints for arXiv tool search and import logs.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/agent/arxiv/logs")
@Validated
public class AdminArxivToolLogController {

    private final ArxivToolLogQueryService logQueryService;
    private final Scheduler blockingScheduler;

    @RbacApis({
            @RbacApi(appCode = "agent", code = "api:agent:arxiv-log:collection", name = "arXiv 工具日志集合",
                    method = "GET", pattern = "/api/admin/agent/arxiv/logs", risk = ApiRiskLevel.HIGH),
            @RbacApi(appCode = "agent", code = "api:agent:arxiv-log:*", name = "arXiv 工具日志管理",
                    method = "ANY", pattern = "/api/admin/agent/arxiv/logs/**", matcher = ApiMatcherType.ANT, risk = ApiRiskLevel.HIGH)
    })
    @GetMapping
    public Mono<ApiResponse<PageResult<ArxivToolLogResponse>>> page(@RequestParam(defaultValue = "1") @Min(1) int page,
                                                                    @RequestParam(defaultValue = "20") @Min(1) int size,
                                                                    @AuthenticationPrincipal RbacPrincipal principal) {
        return Mono.deferContextual(contextView -> {
            String traceId = TraceContext.traceId(contextView);
            return Mono.fromCallable(() -> TraceContext.withMdc(traceId, () -> {
                        var result = logQueryService.page(
                                PageRequest.of(Math.max(page - 1, 0), size, Sort.by("id").descending()),
                                principal);
                        return ApiResponse.ok(new PageResult<>(result.getContent(), result.getNumber() + 1,
                                result.getSize(), result.getTotalElements(), result.getTotalPages()), traceId);
                    }))
                    .subscribeOn(blockingScheduler);
        });
    }

}
