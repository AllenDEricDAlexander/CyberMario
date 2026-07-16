package top.egon.mario.investment.research.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.common.web.ReactiveInvestmentSupport;
import top.egon.mario.investment.research.indicator.InvestmentIndicatorService;
import top.egon.mario.investment.research.indicator.InvestmentIndicatorSnapshot;
import top.egon.mario.investment.research.report.InvestmentReportService;
import top.egon.mario.investment.research.web.dto.CreateInvestmentReportRequest;
import top.egon.mario.investment.research.web.dto.CreateInvestmentReportResponse;
import top.egon.mario.investment.research.web.dto.InvestmentReportDetailResponse;
import top.egon.mario.investment.research.web.dto.InvestmentReportSummaryResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;

/**
 * Reactive API for deterministic indicators and owner-scoped asynchronous reports.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/investment")
@Validated
public class InvestmentResearchController extends ReactiveInvestmentSupport {

    private final InvestmentIndicatorService indicatorService;
    private final InvestmentReportService reportService;

    @GetMapping("/market/instruments/{instrumentId}/indicators")
    public Mono<ApiResponse<InvestmentIndicatorSnapshot>> indicators(
            @PathVariable @Min(1) long instrumentId,
            @RequestParam PriceType priceType,
            @RequestParam BarInterval interval,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dataAsOf) {
        return blocking(() -> indicatorService.calculate(
                instrumentId, priceType, interval, from, to, dataAsOf));
    }

    @GetMapping("/workspaces/{workspaceId}/reports")
    public Mono<ApiResponse<PageResult<InvestmentReportSummaryResponse>>> reports(
            @PathVariable @Min(1) Long workspaceId,
            @RequestParam(required = false) String reportType,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> pageResult(reportService.list(actorId(principal), workspaceId, reportType,
                PageRequest.of(page - 1, size, Sort.by("createdAt").descending()
                        .and(Sort.by("id").descending())))));
    }

    @PostMapping("/workspaces/{workspaceId}/reports")
    public Mono<ApiResponse<CreateInvestmentReportResponse>> createReport(
            @PathVariable @Min(1) Long workspaceId,
            @Valid @RequestBody CreateInvestmentReportRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> reportService.create(actorId(principal), workspaceId, request));
    }

    @GetMapping("/reports/{reportId}")
    public Mono<ApiResponse<InvestmentReportDetailResponse>> report(
            @PathVariable @Min(1) Long reportId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> reportService.detail(actorId(principal), reportId));
    }

    private static <T> PageResult<T> pageResult(Page<T> page) {
        return new PageResult<>(
                page.getContent(), page.getNumber() + 1, page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
