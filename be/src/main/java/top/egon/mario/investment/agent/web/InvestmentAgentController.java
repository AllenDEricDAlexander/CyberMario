package top.egon.mario.investment.agent.web;

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
import top.egon.mario.investment.agent.service.InvestmentAgentRunService;
import top.egon.mario.investment.agent.web.dto.InvestmentAgentDecisionResponse;
import top.egon.mario.investment.agent.web.dto.InvestmentAgentExecutionResponse;
import top.egon.mario.investment.agent.web.dto.InvestmentAgentRunDetailResponse;
import top.egon.mario.investment.agent.web.dto.InvestmentAgentRunResponse;
import top.egon.mario.investment.agent.web.dto.SubmitInvestmentAgentRunRequest;
import top.egon.mario.investment.agent.web.dto.SubmitInvestmentAgentRunResponse;
import top.egon.mario.investment.common.web.InvestmentDecimalCodec;
import top.egon.mario.investment.common.web.ReactiveInvestmentSupport;
import top.egon.mario.investment.trading.po.InvestmentTradeIntentPo;
import top.egon.mario.investment.trading.repository.InvestmentTradeIntentRepository;
import top.egon.mario.investment.trading.service.PaperOrderService;
import top.egon.mario.investment.trading.service.model.PaperTradeResult;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.math.BigDecimal;

/** Owner-only Investment Agent API. There is intentionally no prompt editor or execution-confirmation endpoint. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/investment")
@Validated
public class InvestmentAgentController extends ReactiveInvestmentSupport {

    private final InvestmentAgentRunService runService;
    private final InvestmentTradeIntentRepository intentRepository;
    private final PaperOrderService paperOrderService;

    @PostMapping("/workspaces/{workspaceId}/agent-runs")
    public Mono<ApiResponse<SubmitInvestmentAgentRunResponse>> submit(
            @PathVariable @Min(1) long workspaceId,
            @Valid @RequestBody SubmitInvestmentAgentRunRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> {
            InvestmentAgentRunService.Submission submission = runService.submit(
                    actorId(principal), principal == null ? null : principal.username(), workspaceId,
                    new InvestmentAgentRunService.SubmitCommand(
                            request.runType(), request.accountId(), request.instrumentIds()));
            return new SubmitInvestmentAgentRunResponse(
                    run(submission.run()), submission.jobId(), submission.duplicate());
        });
    }

    @GetMapping("/workspaces/{workspaceId}/agent-runs")
    public Mono<ApiResponse<PageResult<InvestmentAgentRunResponse>>> list(
            @PathVariable @Min(1) long workspaceId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> page(runService.listOwned(actorId(principal), workspaceId,
                PageRequest.of(page - 1, size, Sort.by("createdAt").descending()
                        .and(Sort.by("id").descending()))).map(InvestmentAgentController::run)));
    }

    @GetMapping("/agent-runs/{runId}")
    public Mono<ApiResponse<InvestmentAgentRunDetailResponse>> detail(
            @PathVariable @Min(1) long runId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> detail(runService.ownedDetail(actorId(principal), runId)));
    }

    private InvestmentAgentRunDetailResponse detail(InvestmentAgentRunService.RunDetail detail) {
        return new InvestmentAgentRunDetailResponse(run(detail.run()), detail.decisions().stream()
                .map(this::decision).toList());
    }

    private InvestmentAgentDecisionResponse decision(InvestmentAgentRunService.DecisionSummary value) {
        return new InvestmentAgentDecisionResponse(
                value.id(), value.instrumentId(), value.action(), decimal(value.confidence()), value.horizon(),
                value.thesis(), value.risks(), value.invalidation(), decimal(value.requestedQuantity()),
                decimal(value.requestedNotional()), decimal(value.requestedLeverage()), value.orderType(),
                decimal(value.limitPrice()), value.intentId(), value.executionStatus(), value.dataAsOf(),
                value.expiresAt(), value.status(), value.createdAt(), execution(value.intentId()));
    }

    private InvestmentAgentExecutionResponse execution(Long intentId) {
        if (intentId == null) {
            return null;
        }
        InvestmentTradeIntentPo intent = intentRepository.findById(intentId).orElse(null);
        if (intent == null) {
            return null;
        }
        PaperTradeResult result = paperOrderService.result(intent);
        return new InvestmentAgentExecutionResponse(
                result.intentId(), result.intentStatus(), result.riskResults().stream()
                .map(risk -> new InvestmentAgentExecutionResponse.RiskCheck(
                        risk.ruleCode().name(), risk.passed(), decimal(risk.observedValue()),
                        decimal(risk.limitValue()), risk.message(), risk.details(), risk.checkedAt()))
                .toList(),
                result.order() == null ? null : new InvestmentAgentExecutionResponse.Order(
                        result.order().orderId(), result.order().status(), result.order().submittedAt(),
                        result.order().matchedAt()),
                result.fill() == null ? null : new InvestmentAgentExecutionResponse.Fill(
                        result.fill().fillId(), decimal(result.fill().fillPrice()),
                        decimal(result.fill().quantity()), decimal(result.fill().feeAmount()),
                        result.fill().filledAt()));
    }

    private static InvestmentAgentRunResponse run(InvestmentAgentRunService.RunSummary value) {
        return new InvestmentAgentRunResponse(
                value.id(), value.workspaceId(), value.accountId(), value.presetCode(),
                value.genericAgentRunAuditId(), value.runType(), value.status(), value.dataAsOf(), value.reportId(),
                value.startedAt(), value.finishedAt(), value.errorCode(), value.errorMessage(), value.createdAt());
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : InvestmentDecimalCodec.format(value);
    }

    private static <T> PageResult<T> page(Page<T> page) {
        return new PageResult<>(page.getContent(), page.getNumber() + 1, page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
