package top.egon.mario.investment.portfolio.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.investment.common.web.ReactiveInvestmentSupport;
import top.egon.mario.investment.portfolio.query.InvestmentPortfolioQueryService;
import top.egon.mario.investment.portfolio.web.dto.InvestmentEquityResponse;
import top.egon.mario.investment.portfolio.web.dto.InvestmentFillMarkerResponse;
import top.egon.mario.investment.portfolio.web.dto.InvestmentLedgerResponse;
import top.egon.mario.investment.portfolio.web.dto.InvestmentPositionResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.List;

/** Owner-scoped paper portfolio, marker, ledger, and equity endpoints. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/investment/paper-accounts/{accountId}")
@Validated
public class InvestmentPortfolioController extends ReactiveInvestmentSupport {

    private final InvestmentPortfolioQueryService queryService;

    @GetMapping("/fills")
    public Mono<ApiResponse<PageResult<InvestmentFillMarkerResponse>>> fills(
            @PathVariable @Min(1) Long accountId,
            @RequestParam @Min(1) Long instrumentId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int size,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> page(queryService.fills(
                actorId(principal), accountId, instrumentId, from, to, page - 1, size)));
    }

    @GetMapping("/positions")
    public Mono<ApiResponse<List<InvestmentPositionResponse>>> positions(
            @PathVariable @Min(1) Long accountId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> queryService.positions(actorId(principal), accountId));
    }

    @GetMapping("/ledger")
    public Mono<ApiResponse<PageResult<InvestmentLedgerResponse>>> ledger(
            @PathVariable @Min(1) Long accountId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> page(queryService.ledger(actorId(principal), accountId, page - 1, size)));
    }

    @GetMapping("/equity")
    public Mono<ApiResponse<PageResult<InvestmentEquityResponse>>> equity(
            @PathVariable @Min(1) Long accountId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int size,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> page(queryService.equity(actorId(principal), accountId, page - 1, size)));
    }

    private static <T> PageResult<T> page(org.springframework.data.domain.Page<T> page) {
        return new PageResult<>(
                page.getContent(), page.getNumber() + 1, page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
