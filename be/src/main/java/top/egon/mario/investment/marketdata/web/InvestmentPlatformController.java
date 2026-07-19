package top.egon.mario.investment.marketdata.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.web.ReactiveInvestmentSupport;
import top.egon.mario.investment.marketdata.query.InvestmentPlatformQueryService;
import top.egon.mario.investment.marketdata.web.dto.InvestmentDataQualityIssueResponse;
import top.egon.mario.investment.marketdata.web.dto.InvestmentPlatformJobResponse;
import top.egon.mario.investment.marketdata.web.dto.InvestmentPlatformSubscriptionResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

/**
 * Platform-admin API; code subscriptions expose no create, update or delete operation.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/investment/platform")
@Validated
public class InvestmentPlatformController extends ReactiveInvestmentSupport {

    private static final String PLATFORM_ADMIN = "INVESTMENT_PLATFORM_ADMIN";
    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final InvestmentPlatformQueryService queryService;

    @GetMapping("/subscriptions")
    public Mono<ApiResponse<List<InvestmentPlatformSubscriptionResponse>>> subscriptions(
            @AuthenticationPrincipal RbacPrincipal principal) {
        requirePlatformAdmin(principal);
        return blocking(queryService::subscriptions);
    }

    @GetMapping("/jobs")
    public Mono<ApiResponse<PageResult<InvestmentPlatformJobResponse>>> jobs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String jobType,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal RbacPrincipal principal) {
        requirePlatformAdmin(principal);
        return blocking(() -> queryService.jobs(status, jobType, page, size));
    }

    @PostMapping("/jobs/{jobId}/retry")
    public Mono<ApiResponse<Void>> retryJob(
            @PathVariable @Min(1) long jobId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        requirePlatformAdmin(principal);
        return blockingVoid(() -> queryService.retryFailedJob(jobId));
    }

    @GetMapping("/data-quality-issues")
    public Mono<ApiResponse<PageResult<InvestmentDataQualityIssueResponse>>> qualityIssues(
            @RequestParam(required = false) String resolutionStatus,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal RbacPrincipal principal) {
        requirePlatformAdmin(principal);
        return blocking(() -> queryService.qualityIssues(resolutionStatus, severity, page, size));
    }

    @PostMapping("/data-quality-issues/{issueId}/resolve")
    public Mono<ApiResponse<Void>> resolveQualityIssue(
            @PathVariable @Min(1) long issueId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        requirePlatformAdmin(principal);
        return blockingVoid(() -> queryService.resolveQualityIssue(issueId, principal.userId()));
    }

    private void requirePlatformAdmin(RbacPrincipal principal) {
        if (principal == null || principal.roleCodes() == null
                || (!principal.roleCodes().contains(PLATFORM_ADMIN)
                && !principal.roleCodes().contains(SUPER_ADMIN))) {
            throw new InvestmentException(InvestmentErrorCode.FORBIDDEN,
                    "Investment platform administrator role required");
        }
    }
}
