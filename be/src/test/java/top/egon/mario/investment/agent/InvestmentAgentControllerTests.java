package top.egon.mario.investment.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.investment.agent.model.InvestmentAgentAction;
import top.egon.mario.investment.agent.model.InvestmentAgentExecutionStatus;
import top.egon.mario.investment.agent.model.InvestmentAgentRunType;
import top.egon.mario.investment.agent.service.InvestmentAgentRunService;
import top.egon.mario.investment.agent.web.InvestmentAgentController;
import top.egon.mario.investment.agent.web.dto.SubmitInvestmentAgentRunRequest;
import top.egon.mario.investment.common.model.InvestmentRunStatus;
import top.egon.mario.investment.common.model.OrderType;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskCheckResult;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskRuleCode;
import top.egon.mario.investment.trading.po.InvestmentTradeIntentPo;
import top.egon.mario.investment.trading.repository.InvestmentTradeIntentRepository;
import top.egon.mario.investment.trading.service.PaperOrderService;
import top.egon.mario.investment.trading.service.model.PaperFillSummary;
import top.egon.mario.investment.trading.service.model.PaperOrderSummary;
import top.egon.mario.investment.trading.service.model.PaperTradeResult;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvestmentAgentControllerTests {

    private static final Instant CUTOFF = Instant.parse("2035-01-01T00:00:00Z");

    private InvestmentAgentRunService runService;
    private InvestmentTradeIntentRepository intentRepository;
    private PaperOrderService orderService;
    private InvestmentAgentController controller;
    private RbacPrincipal principal;

    @BeforeEach
    void setUp() {
        runService = mock(InvestmentAgentRunService.class);
        intentRepository = mock(InvestmentTradeIntentRepository.class);
        orderService = mock(PaperOrderService.class);
        controller = new InvestmentAgentController(runService, intentRepository, orderService);
        ReflectionTestUtils.setField(controller, "blockingScheduler", Schedulers.immediate());
        principal = new RbacPrincipal(5L, "owner", Set.of("INVESTMENT_USER"), Set.of(), "v1");
    }

    @Test
    void submitBindsActorAndFixedRequestHasNoPromptToolOrStrategyEditorFields() {
        SubmitInvestmentAgentRunRequest request = new SubmitInvestmentAgentRunRequest(
                InvestmentAgentRunType.AUTO_TRADE, 31L, List.of(11L));
        when(runService.submit(5L, "owner", 7L, new InvestmentAgentRunService.SubmitCommand(
                InvestmentAgentRunType.AUTO_TRADE, 31L, List.of(11L))))
                .thenReturn(new InvestmentAgentRunService.Submission(run(), 71L, false));

        StepVerifier.create(controller.submit(7L, request, principal))
                .assertNext(response -> {
                    assertThat(response.data().run().presetCode()).isEqualTo("INVESTMENT_ANALYST_V1");
                    assertThat(response.data().jobId()).isEqualTo(71L);
                    assertThat(response.data().duplicate()).isFalse();
                }).verifyComplete();

        assertThat(Arrays.stream(SubmitInvestmentAgentRunRequest.class.getRecordComponents())
                .map(component -> component.getName()).toList())
                .containsExactly("runType", "accountId", "instrumentIds")
                .noneMatch(name -> name.contains("prompt") || name.contains("tool") || name.contains("strategy"));
        verify(runService).submit(5L, "owner", 7L, new InvestmentAgentRunService.SubmitCommand(
                InvestmentAgentRunType.AUTO_TRADE, 31L, List.of(11L)));
    }

    @Test
    void detailExplainsDecisionIntentRiskPendingOrderAndFillUsingDecimalStrings() {
        InvestmentAgentRunService.DecisionSummary decision = new InvestmentAgentRunService.DecisionSummary(
                51L, 11L, InvestmentAgentAction.OPEN_LONG, new BigDecimal("0.7500"), "INTRADAY", "trend",
                List.of("volatility"), List.of("support lost"), new BigDecimal("1.000"),
                new BigDecimal("100.00"), new BigDecimal("2"), OrderType.MARKET, null, 61L,
                InvestmentAgentExecutionStatus.SUBMITTED, CUTOFF, null, "VALIDATED", CUTOFF);
        when(runService.ownedDetail(5L, 41L))
                .thenReturn(new InvestmentAgentRunService.RunDetail(run(), List.of(decision)));
        InvestmentTradeIntentPo intent = new InvestmentTradeIntentPo();
        intent.setId(61L);
        when(intentRepository.findById(61L)).thenReturn(Optional.of(intent));
        InvestmentRiskCheckResult risk = new InvestmentRiskCheckResult(
                InvestmentRiskRuleCode.ORDER_NOTIONAL_LIMIT, true, new BigDecimal("100.00"),
                new BigDecimal("1000"), "ok", Map.of("group", "EXPOSURE"), CUTOFF);
        when(orderService.result(intent)).thenReturn(new PaperTradeResult(
                61L, "ACCEPTED", List.of(risk),
                new PaperOrderSummary(81L, "PENDING_MATCH", CUTOFF, null),
                new PaperFillSummary(91L, new BigDecimal("100.5"), new BigDecimal("1"),
                        new BigDecimal("0.05"), CUTOFF.plusSeconds(60))));

        StepVerifier.create(controller.detail(41L, principal))
                .assertNext(response -> {
                    var value = response.data().decisions().getFirst();
                    assertThat(value.confidence()).isEqualTo("0.7500");
                    assertThat(value.execution().intentStatus()).isEqualTo("ACCEPTED");
                    assertThat(value.execution().riskChecks().getFirst().observedValue()).isEqualTo("100.00");
                    assertThat(value.execution().order().status()).isEqualTo("PENDING_MATCH");
                    assertThat(value.execution().fill().price()).isEqualTo("100.5");
                }).verifyComplete();

        verify(runService).ownedDetail(5L, 41L);
    }

    private static InvestmentAgentRunService.RunSummary run() {
        return new InvestmentAgentRunService.RunSummary(
                41L, 7L, 31L, "INVESTMENT_ANALYST_V1", 61L, InvestmentAgentRunType.AUTO_TRADE,
                InvestmentRunStatus.RUNNING, CUTOFF, null, CUTOFF, null, null, null, CUTOFF);
    }
}
