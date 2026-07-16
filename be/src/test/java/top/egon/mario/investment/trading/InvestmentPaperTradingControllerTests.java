package top.egon.mario.investment.trading;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.investment.common.model.OrderType;
import top.egon.mario.investment.common.model.PositionAction;
import top.egon.mario.investment.common.model.PositionSide;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskCheckResult;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskRuleCode;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskSource;
import top.egon.mario.investment.trading.service.PaperOrderService;
import top.egon.mario.investment.trading.service.PaperTradingFacade;
import top.egon.mario.investment.trading.service.model.PaperOrderSummary;
import top.egon.mario.investment.trading.service.model.PaperTradeCommand;
import top.egon.mario.investment.trading.service.model.PaperTradeResult;
import top.egon.mario.investment.trading.web.InvestmentPaperTradingController;
import top.egon.mario.investment.trading.web.dto.SubmitPaperTradeIntentRequest;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestmentPaperTradingControllerTests {

    private static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");

    @Mock
    private PaperTradingFacade facade;
    @Mock
    private PaperOrderService orderService;

    private InvestmentPaperTradingController controller;
    private RbacPrincipal principal;

    @BeforeEach
    void setUp() {
        controller = new InvestmentPaperTradingController(facade, orderService);
        ReflectionTestUtils.setField(controller, "blockingScheduler", Schedulers.immediate());
        principal = new RbacPrincipal(101L, "owner", Set.of("INVESTMENT_USER"), Set.of(), "v1");
    }

    @Test
    void manualRequestBindsActorAndUserSourceAndReturnsPendingMatchWithRiskDetails() {
        SubmitPaperTradeIntentRequest request = new SubmitPaperTradeIntentRequest(
                501L, PositionAction.OPEN, PositionSide.LONG, OrderType.MARKET,
                "1.000", "100.00", "5", null, false, "manual", NOW, null, "manual-1");
        InvestmentRiskCheckResult risk = new InvestmentRiskCheckResult(
                InvestmentRiskRuleCode.ORDER_NOTIONAL_LIMIT, true, new BigDecimal("100.00"),
                new BigDecimal("1000"), "ok", Map.of("group", "EXPOSURE"), NOW);
        PaperTradeResult result = new PaperTradeResult(
                31L, "ACCEPTED", List.of(risk),
                new PaperOrderSummary(41L, "PENDING_MATCH", NOW, null), null);
        when(facade.submitIntent(org.mockito.ArgumentMatchers.any())).thenReturn(result);

        StepVerifier.create(controller.submit(21L, request, principal))
                .assertNext(response -> {
                    assertThat(response.data().intentStatus()).isEqualTo("ACCEPTED");
                    assertThat(response.data().order().status()).isEqualTo("PENDING_MATCH");
                    assertThat(response.data().riskResults().getFirst().observedValue()).isEqualTo("100.00");
                })
                .verifyComplete();

        ArgumentCaptor<PaperTradeCommand> command = ArgumentCaptor.forClass(PaperTradeCommand.class);
        verify(facade).submitIntent(command.capture());
        assertThat(command.getValue().actorId()).isEqualTo(101L);
        assertThat(command.getValue().accountId()).isEqualTo(21L);
        assertThat(command.getValue().source()).isEqualTo(InvestmentRiskSource.USER);
        assertThat(command.getValue().callerLimits()).isNull();
    }

    @Test
    void cancellationUsesOnlyAuthenticatedOwnerAndServerResolvedOrderScope() {
        PaperOrderSummary cancelled = new PaperOrderSummary(41L, "CANCELLED", NOW, null);
        when(orderService.cancelOwned(101L, 41L)).thenReturn(cancelled);

        StepVerifier.create(controller.cancel(41L, principal))
                .assertNext(response -> assertThat(response.data().status()).isEqualTo("CANCELLED"))
                .verifyComplete();

        verify(orderService).cancelOwned(101L, 41L);
    }
}
