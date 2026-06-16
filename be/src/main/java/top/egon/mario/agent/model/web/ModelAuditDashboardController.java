package top.egon.mario.agent.model.web;

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
import top.egon.mario.agent.model.dto.enums.ModelProviderType;
import top.egon.mario.agent.model.dto.enums.ModelScenario;
import top.egon.mario.agent.model.dto.request.ModelAuditDashboardQuery;
import top.egon.mario.agent.model.dto.response.ModelAuditDashboardSummaryResponse;
import top.egon.mario.agent.model.dto.response.ModelAuditRecentCallResponse;
import top.egon.mario.agent.model.dto.response.ModelAuditUserOptionResponse;
import top.egon.mario.agent.model.po.enums.ModelAuditStatus;
import top.egon.mario.agent.model.service.ModelAuditDashboardService;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;
import top.egon.mario.rbac.service.resource.annotation.RbacApi;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/**
 * Model audit dashboard endpoints for personal and global usage views.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent/model-audit/dashboard")
@Validated
public class ModelAuditDashboardController {

    private final ModelAuditDashboardService dashboardService;
    private final Scheduler blockingScheduler;

    @GetMapping("/self/summary")
    public Mono<ApiResponse<ModelAuditDashboardSummaryResponse>> selfSummary(@RequestParam(required = false) Instant startAt,
                                                                             @RequestParam(required = false) Instant endAt,
                                                                             @RequestParam(required = false) ModelProviderType provider,
                                                                             @RequestParam(required = false) String model,
                                                                             @RequestParam(required = false) ModelScenario scenario,
                                                                             @RequestParam(required = false) ModelAuditStatus status,
                                                                             @AuthenticationPrincipal RbacPrincipal principal) {
        ModelAuditDashboardQuery query = new ModelAuditDashboardQuery(startAt, endAt, null, provider, model, scenario, status);
        return blocking(() -> dashboardService.selfSummary(query, principal));
    }

    @GetMapping("/self/recent-calls")
    public Mono<ApiResponse<PageResult<ModelAuditRecentCallResponse>>> selfRecentCalls(@RequestParam(required = false) Instant startAt,
                                                                                       @RequestParam(required = false) Instant endAt,
                                                                                       @RequestParam(required = false) ModelProviderType provider,
                                                                                       @RequestParam(required = false) String model,
                                                                                       @RequestParam(required = false) ModelScenario scenario,
                                                                                       @RequestParam(required = false) ModelAuditStatus status,
                                                                                       @RequestParam(defaultValue = "1") @Min(1) int page,
                                                                                       @RequestParam(defaultValue = "20") @Min(1) int size,
                                                                                       @AuthenticationPrincipal RbacPrincipal principal) {
        ModelAuditDashboardQuery query = new ModelAuditDashboardQuery(startAt, endAt, null, provider, model, scenario, status);
        return blocking(() -> pageResult(dashboardService.selfRecentCalls(query,
                PageRequest.of(Math.max(page - 1, 0), size, Sort.by("createdAt").descending().and(Sort.by("id").descending())),
                principal)));
    }

    @GetMapping("/global/summary")
    public Mono<ApiResponse<ModelAuditDashboardSummaryResponse>> globalSummary(@RequestParam(required = false) Instant startAt,
                                                                               @RequestParam(required = false) Instant endAt,
                                                                               @RequestParam(required = false) Long userId,
                                                                               @RequestParam(required = false) ModelProviderType provider,
                                                                               @RequestParam(required = false) String model,
                                                                               @RequestParam(required = false) ModelScenario scenario,
                                                                               @RequestParam(required = false) ModelAuditStatus status,
                                                                               @AuthenticationPrincipal RbacPrincipal principal) {
        ModelAuditDashboardQuery query = new ModelAuditDashboardQuery(startAt, endAt, userId, provider, model, scenario, status);
        return blocking(() -> dashboardService.globalSummary(query, principal));
    }

    @GetMapping("/global/recent-calls")
    public Mono<ApiResponse<PageResult<ModelAuditRecentCallResponse>>> globalRecentCalls(@RequestParam(required = false) Instant startAt,
                                                                                         @RequestParam(required = false) Instant endAt,
                                                                                         @RequestParam(required = false) Long userId,
                                                                                         @RequestParam(required = false) ModelProviderType provider,
                                                                                         @RequestParam(required = false) String model,
                                                                                         @RequestParam(required = false) ModelScenario scenario,
                                                                                         @RequestParam(required = false) ModelAuditStatus status,
                                                                                         @RequestParam(defaultValue = "1") @Min(1) int page,
                                                                                         @RequestParam(defaultValue = "20") @Min(1) int size,
                                                                                         @AuthenticationPrincipal RbacPrincipal principal) {
        ModelAuditDashboardQuery query = new ModelAuditDashboardQuery(startAt, endAt, userId, provider, model, scenario, status);
        return blocking(() -> pageResult(dashboardService.globalRecentCalls(query,
                PageRequest.of(Math.max(page - 1, 0), size, Sort.by("createdAt").descending().and(Sort.by("id").descending())),
                principal)));
    }

    @RbacApi(appCode = "agent", code = "api:agent:model-audit:dashboard:user-options", name = "AI 用量控制台用户选项",
            method = "GET", pattern = "/api/agent/model-audit/dashboard/user-options", risk = ApiRiskLevel.HIGH)
    @GetMapping("/user-options")
    public Mono<ApiResponse<List<ModelAuditUserOptionResponse>>> userOptions(@RequestParam(required = false) String keyword,
                                                                             @RequestParam(defaultValue = "20") @Min(1) int size,
                                                                             @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> dashboardService.userOptions(keyword, size, principal));
    }

    private <T> PageResult<T> pageResult(org.springframework.data.domain.Page<T> page) {
        return new PageResult<>(page.getContent(), page.getNumber() + 1, page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    private <T> Mono<ApiResponse<T>> blocking(Supplier<T> supplier) {
        return Mono.deferContextual(contextView -> {
            String traceId = TraceContext.traceId(contextView);
            return Mono.fromCallable(() -> TraceContext.withMdc(traceId, supplier))
                    .map(data -> ApiResponse.ok(data, traceId))
                    .subscribeOn(blockingScheduler);
        });
    }

}
