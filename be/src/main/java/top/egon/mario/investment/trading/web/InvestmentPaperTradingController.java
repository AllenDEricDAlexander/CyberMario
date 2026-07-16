package top.egon.mario.investment.trading.web;

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
import top.egon.mario.investment.common.web.InvestmentDecimalCodec;
import top.egon.mario.investment.common.web.ReactiveInvestmentSupport;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskCheckResult;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskSource;
import top.egon.mario.investment.trading.service.PaperOrderService;
import top.egon.mario.investment.trading.service.PaperTradingFacade;
import top.egon.mario.investment.trading.service.model.PaperFillSummary;
import top.egon.mario.investment.trading.service.model.PaperOrderSummary;
import top.egon.mario.investment.trading.service.model.PaperTradeCommand;
import top.egon.mario.investment.trading.service.model.PaperTradeResult;
import top.egon.mario.investment.trading.web.dto.PaperFillResponse;
import top.egon.mario.investment.trading.web.dto.PaperOrderResponse;
import top.egon.mario.investment.trading.web.dto.PaperRiskCheckResponse;
import top.egon.mario.investment.trading.web.dto.PaperTradeIntentResponse;
import top.egon.mario.investment.trading.web.dto.SubmitPaperTradeIntentRequest;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.math.BigDecimal;

/**
 * Manual paper-trading API; all writes still enter the shared deterministic facade.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/investment")
@Validated
public class InvestmentPaperTradingController extends ReactiveInvestmentSupport {

    private final PaperTradingFacade tradingFacade;
    private final PaperOrderService orderService;

    @PostMapping("/paper-accounts/{accountId}/trade-intents")
    public Mono<ApiResponse<PaperTradeIntentResponse>> submit(
            @PathVariable @Min(1) Long accountId,
            @Valid @RequestBody SubmitPaperTradeIntentRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> toResponse(tradingFacade.submitIntent(new PaperTradeCommand(
                actorId(principal), accountId, request.instrumentId(), InvestmentRiskSource.USER,
                "user:" + actorId(principal), request.idempotencyKey(), request.positionAction(),
                request.positionSide(), request.orderType(), decimal(request.quantity()),
                decimal(request.requestedNotional()), decimal(request.leverage()),
                nullableDecimal(request.limitPrice()), request.reduceOnly(), request.reason(),
                request.dataAsOf(), request.expiresAt(), null))));
    }

    @GetMapping("/paper-accounts/{accountId}/orders")
    public Mono<ApiResponse<PageResult<PaperOrderResponse>>> listOrders(
            @PathVariable @Min(1) Long accountId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> pageResult(orderService.listOwned(
                actorId(principal), accountId,
                PageRequest.of(page - 1, size, Sort.by("submittedAt").descending()
                        .and(Sort.by("id").descending()))).map(InvestmentPaperTradingController::toOrder)));
    }

    @PostMapping("/paper-orders/{orderId}/cancel")
    public Mono<ApiResponse<PaperOrderResponse>> cancel(
            @PathVariable @Min(1) Long orderId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> toOrder(orderService.cancelOwned(actorId(principal), orderId)));
    }

    private static PaperTradeIntentResponse toResponse(PaperTradeResult result) {
        return new PaperTradeIntentResponse(
                result.intentId(), result.intentStatus(), result.riskResults().stream()
                .map(InvestmentPaperTradingController::toRisk).toList(),
                result.order() == null ? null : toOrder(result.order()),
                result.fill() == null ? null : toFill(result.fill()));
    }

    private static PaperRiskCheckResponse toRisk(InvestmentRiskCheckResult result) {
        return new PaperRiskCheckResponse(
                result.ruleCode().name(), result.passed(), format(result.observedValue()),
                format(result.limitValue()), result.message(), result.details(), result.checkedAt());
    }

    private static PaperOrderResponse toOrder(PaperOrderSummary order) {
        return new PaperOrderResponse(order.orderId(), order.status(), order.submittedAt(), order.matchedAt());
    }

    private static PaperFillResponse toFill(PaperFillSummary fill) {
        return new PaperFillResponse(
                fill.fillId(), format(fill.fillPrice()), format(fill.quantity()),
                format(fill.feeAmount()), fill.filledAt());
    }

    private static BigDecimal decimal(String value) {
        return InvestmentDecimalCodec.parse(value);
    }

    private static BigDecimal nullableDecimal(String value) {
        return value == null ? null : decimal(value);
    }

    private static String format(BigDecimal value) {
        return value == null ? null : InvestmentDecimalCodec.format(value);
    }

    private static <T> PageResult<T> pageResult(Page<T> page) {
        return new PageResult<>(
                page.getContent(), page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
