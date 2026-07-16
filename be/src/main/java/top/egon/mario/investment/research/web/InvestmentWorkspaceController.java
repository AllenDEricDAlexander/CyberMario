package top.egon.mario.investment.research.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import top.egon.mario.investment.common.web.ReactiveInvestmentSupport;
import top.egon.mario.investment.research.service.InvestmentWatchlistService;
import top.egon.mario.investment.research.service.InvestmentWorkspaceService;
import top.egon.mario.investment.research.web.dto.AddInvestmentWatchlistItemRequest;
import top.egon.mario.investment.research.web.dto.CreateInvestmentWatchlistRequest;
import top.egon.mario.investment.research.web.dto.CreateInvestmentWorkspaceRequest;
import top.egon.mario.investment.research.web.dto.InvestmentWatchlistItemResponse;
import top.egon.mario.investment.research.web.dto.InvestmentWatchlistResponse;
import top.egon.mario.investment.research.web.dto.InvestmentWorkspaceResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

/**
 * Reactive API for owner-scoped Investment workspaces and watchlists.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/investment")
@Validated
public class InvestmentWorkspaceController extends ReactiveInvestmentSupport {

    private final InvestmentWorkspaceService workspaceService;
    private final InvestmentWatchlistService watchlistService;

    @GetMapping("/workspaces")
    public Mono<ApiResponse<PageResult<InvestmentWorkspaceResponse>>> listWorkspaces(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> pageResult(workspaceService.list(
                actorId(principal), PageRequest.of(page - 1, size, Sort.by("createdAt").descending()
                        .and(Sort.by("id").descending())))));
    }

    @PostMapping("/workspaces")
    public Mono<ApiResponse<InvestmentWorkspaceResponse>> createWorkspace(
            @Valid @RequestBody CreateInvestmentWorkspaceRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> workspaceService.create(actorId(principal), request));
    }

    @GetMapping("/workspaces/{workspaceId}/watchlists")
    public Mono<ApiResponse<PageResult<InvestmentWatchlistResponse>>> listWatchlists(
            @PathVariable @Min(1) Long workspaceId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> pageResult(watchlistService.list(
                actorId(principal), workspaceId,
                PageRequest.of(page - 1, size, Sort.by("sortNo").ascending().and(Sort.by("id").ascending())))));
    }

    @PostMapping("/workspaces/{workspaceId}/watchlists")
    public Mono<ApiResponse<InvestmentWatchlistResponse>> createWatchlist(
            @PathVariable @Min(1) Long workspaceId,
            @Valid @RequestBody CreateInvestmentWatchlistRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> watchlistService.create(actorId(principal), workspaceId, request));
    }

    @PostMapping("/watchlists/{watchlistId}/items")
    public Mono<ApiResponse<InvestmentWatchlistItemResponse>> addWatchlistItem(
            @PathVariable @Min(1) Long watchlistId,
            @RequestParam @Min(1) Long workspaceId,
            @Valid @RequestBody AddInvestmentWatchlistItemRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> watchlistService.addItem(
                actorId(principal), workspaceId, watchlistId, request));
    }

    @DeleteMapping("/watchlists/{watchlistId}/items")
    public Mono<ApiResponse<Void>> removeWatchlistItem(
            @PathVariable @Min(1) Long watchlistId,
            @RequestParam @Min(1) Long workspaceId,
            @RequestParam @Min(1) Long instrumentId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blockingVoid(() -> watchlistService.removeItem(
                actorId(principal), workspaceId, watchlistId, instrumentId));
    }

    private static <T> PageResult<T> pageResult(Page<T> page) {
        return new PageResult<>(
                page.getContent(), page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
