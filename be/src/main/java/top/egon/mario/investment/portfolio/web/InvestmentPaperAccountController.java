package top.egon.mario.investment.portfolio.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.investment.common.web.ReactiveInvestmentSupport;
import top.egon.mario.investment.portfolio.service.InvestmentPaperAccountService;
import top.egon.mario.investment.portfolio.web.dto.CreateInvestmentPaperAccountRequest;
import top.egon.mario.investment.portfolio.web.dto.InvestmentPaperAccountDetailResponse;
import top.egon.mario.investment.portfolio.web.dto.InvestmentPaperAccountResponse;
import top.egon.mario.investment.portfolio.web.dto.InvestmentRiskProfileResponse;
import top.egon.mario.investment.portfolio.web.dto.UpdateInvestmentPaperAccountSwitchesRequest;
import top.egon.mario.investment.portfolio.web.dto.UpdateInvestmentRiskProfileRequest;
import top.egon.mario.rbac.service.security.RbacPrincipal;

/**
 * Reactive owner-scoped API for paper accounts, switches, and explicit risk limits.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/investment")
@Validated
public class InvestmentPaperAccountController extends ReactiveInvestmentSupport {

    private final InvestmentPaperAccountService accountService;

    @GetMapping("/workspaces/{workspaceId}/paper-accounts")
    public Mono<ApiResponse<PageResult<InvestmentPaperAccountResponse>>> list(
            @PathVariable @Min(1) Long workspaceId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> pageResult(accountService.list(
                actorId(principal), workspaceId,
                PageRequest.of(page - 1, size, Sort.by("openedAt").descending()
                        .and(Sort.by("id").descending())))));
    }

    @PostMapping("/workspaces/{workspaceId}/paper-accounts")
    public Mono<ApiResponse<InvestmentPaperAccountDetailResponse>> create(
            @PathVariable @Min(1) Long workspaceId,
            @Valid @RequestBody CreateInvestmentPaperAccountRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> accountService.create(actorId(principal), workspaceId, request));
    }

    @GetMapping("/paper-accounts/{accountId}")
    public Mono<ApiResponse<InvestmentPaperAccountDetailResponse>> get(
            @PathVariable @Min(1) Long accountId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> accountService.get(actorId(principal), accountId));
    }

    @PatchMapping("/paper-accounts/{accountId}/switches")
    public Mono<ApiResponse<InvestmentPaperAccountResponse>> updateSwitches(
            @PathVariable @Min(1) Long accountId,
            @Valid @RequestBody UpdateInvestmentPaperAccountSwitchesRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> accountService.updateSwitches(actorId(principal), accountId, request));
    }

    @GetMapping("/paper-accounts/{accountId}/risk-profile")
    public Mono<ApiResponse<InvestmentRiskProfileResponse>> getRiskProfile(
            @PathVariable @Min(1) Long accountId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> accountService.getRiskProfile(actorId(principal), accountId));
    }

    @PutMapping("/paper-accounts/{accountId}/risk-profile")
    public Mono<ApiResponse<InvestmentRiskProfileResponse>> updateRiskProfile(
            @PathVariable @Min(1) Long accountId,
            @Valid @RequestBody UpdateInvestmentRiskProfileRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> accountService.updateRiskProfile(actorId(principal), accountId, request));
    }

    private static <T> PageResult<T> pageResult(Page<T> page) {
        return new PageResult<>(
                page.getContent(), page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
