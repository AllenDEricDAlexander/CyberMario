package top.egon.mario.investment.quant.web;

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
import top.egon.mario.investment.quant.backtest.InvestmentBacktestService;
import top.egon.mario.investment.quant.web.dto.InvestmentBacktestEquityResponse;
import top.egon.mario.investment.quant.web.dto.InvestmentBacktestEventResponse;
import top.egon.mario.investment.quant.web.dto.InvestmentBacktestRunResponse;
import top.egon.mario.investment.quant.web.dto.InvestmentBacktestTradeResponse;
import top.egon.mario.investment.quant.web.dto.SubmitInvestmentBacktestRequest;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/investment")
@Validated
public class InvestmentBacktestController extends ReactiveInvestmentSupport {

    private final InvestmentBacktestService service;

    @PostMapping("/workspaces/{workspaceId}/backtests")
    public Mono<ApiResponse<InvestmentBacktestRunResponse>> submit(
            @PathVariable @Min(1) long workspaceId,
            @Valid @RequestBody SubmitInvestmentBacktestRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> service.submit(actorId(principal), workspaceId, request));
    }

    @GetMapping("/workspaces/{workspaceId}/backtests")
    public Mono<ApiResponse<PageResult<InvestmentBacktestRunResponse>>> list(
            @PathVariable @Min(1) long workspaceId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> page(service.list(actorId(principal), workspaceId,
                PageRequest.of(page - 1, size, Sort.by("createdAt").descending().and(Sort.by("id").descending())))));
    }

    @GetMapping("/backtests/{runId}")
    public Mono<ApiResponse<InvestmentBacktestRunResponse>> detail(
            @PathVariable @Min(1) long runId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> service.detail(actorId(principal), runId));
    }

    @GetMapping("/backtests/{runId}/trades")
    public Mono<ApiResponse<PageResult<InvestmentBacktestTradeResponse>>> trades(
            @PathVariable @Min(1) long runId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> page(service.trades(actorId(principal), runId,
                PageRequest.of(page - 1, size, Sort.by("entryTime").ascending().and(Sort.by("id").ascending())))));
    }

    @GetMapping("/backtests/{runId}/events")
    public Mono<ApiResponse<PageResult<InvestmentBacktestEventResponse>>> events(
            @PathVariable @Min(1) long runId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> page(service.events(actorId(principal), runId,
                PageRequest.of(page - 1, size, Sort.by("sequenceNo").ascending()))));
    }

    @GetMapping("/backtests/{runId}/equity")
    public Mono<ApiResponse<List<InvestmentBacktestEquityResponse>>> equity(
            @PathVariable @Min(1) long runId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> service.equity(actorId(principal), runId));
    }

    private static <T> PageResult<T> page(Page<T> page) {
        return new PageResult<>(page.getContent(), page.getNumber() + 1, page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
