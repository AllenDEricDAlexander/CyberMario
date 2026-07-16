package top.egon.mario.investment.trading;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.egon.mario.investment.common.job.InvestmentJobEnqueueCommand;
import top.egon.mario.investment.common.job.InvestmentJobEnqueueService;
import top.egon.mario.investment.common.model.OrderType;
import top.egon.mario.investment.common.model.PositionAction;
import top.egon.mario.investment.common.model.PositionSide;
import top.egon.mario.investment.portfolio.po.InvestmentPaperAccountPo;
import top.egon.mario.investment.portfolio.po.InvestmentRiskProfilePo;
import top.egon.mario.investment.portfolio.repository.InvestmentPaperAccountRepository;
import top.egon.mario.investment.portfolio.repository.InvestmentPositionRepository;
import top.egon.mario.investment.portfolio.repository.InvestmentRiskProfileRepository;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskRuleCode;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskService;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskSource;
import top.egon.mario.investment.portfolio.risk.rule.ExposureRiskRule;
import top.egon.mario.investment.portfolio.risk.rule.LossRiskRule;
import top.egon.mario.investment.portfolio.risk.rule.MarketRiskRule;
import top.egon.mario.investment.portfolio.risk.rule.OrderRiskRule;
import top.egon.mario.investment.portfolio.risk.rule.SwitchRiskRule;
import top.egon.mario.investment.trading.po.InvestmentPaperOrderPo;
import top.egon.mario.investment.trading.po.InvestmentTradeIntentPo;
import top.egon.mario.investment.trading.repository.InvestmentMarginLedgerRepository;
import top.egon.mario.investment.trading.repository.InvestmentPaperOrderRepository;
import top.egon.mario.investment.trading.repository.InvestmentRiskCheckRepository;
import top.egon.mario.investment.trading.repository.InvestmentTradeIntentRepository;
import top.egon.mario.investment.trading.service.PaperIntentAcceptanceTransactionService;
import top.egon.mario.investment.trading.service.PaperOrderService;
import top.egon.mario.investment.trading.service.model.PaperTradeCommand;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaperIntentAcceptanceTransactionServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");

    @Mock private InvestmentPaperAccountRepository accountRepository;
    @Mock private InvestmentRiskProfileRepository riskProfileRepository;
    @Mock private InvestmentPositionRepository positionRepository;
    @Mock private InvestmentPaperOrderRepository orderRepository;
    @Mock private InvestmentTradeIntentRepository intentRepository;
    @Mock private InvestmentRiskCheckRepository riskCheckRepository;
    @Mock private InvestmentMarginLedgerRepository ledgerRepository;
    @Mock private InvestmentJobEnqueueService enqueueService;
    @Mock private PaperOrderService orderService;

    private PaperIntentAcceptanceTransactionService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        InvestmentRiskService riskService = new InvestmentRiskService(List.of(
                new SwitchRiskRule(), new MarketRiskRule(), new ExposureRiskRule(),
                new LossRiskRule(), new OrderRiskRule()), clock);
        service = new PaperIntentAcceptanceTransactionService(
                accountRepository, riskProfileRepository, positionRepository, orderRepository,
                intentRepository, riskCheckRepository, ledgerRepository, riskService,
                enqueueService, orderService, new ObjectMapper().findAndRegisterModules(), clock);
    }

    @Test
    void acceptedIntentPersistsAllFinalRulesOnePendingOrderAndOneMatchJob() {
        arrangeAccount(true, new BigDecimal("10000"));
        when(intentRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            InvestmentTradeIntentPo intent = invocation.getArgument(0);
            intent.setId(31L);
            return intent;
        });
        when(orderRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            InvestmentPaperOrderPo order = invocation.getArgument(0);
            order.setId(41L);
            return order;
        });

        var result = service.accept(11L, command("accepted", "1", "100"),
                PaperTradingFacadeTests.snapshot());

        assertThat(result.intentStatus()).isEqualTo("ACCEPTED");
        assertThat(result.order().status()).isEqualTo("PENDING_MATCH");
        assertThat(result.riskResults()).hasSize(InvestmentRiskRuleCode.values().length)
                .allMatch(value -> value.passed());
        verify(riskCheckRepository).saveAllAndFlush(
                org.mockito.ArgumentMatchers.argThat(values ->
                        StreamSupport.stream(values.spliterator(), false).count()
                                == InvestmentRiskRuleCode.values().length));
        verify(orderRepository).saveAndFlush(any(InvestmentPaperOrderPo.class));
        verify(enqueueService).enqueue(org.mockito.ArgumentMatchers.argThat(
                command -> command.idempotencyKey().equals("paper-match:41")
                        && command.inputJson().contains("\"contractRevision\"") == false
                        && command.inputJson().contains("\"orderId\":41")));
    }

    @Test
    void disabledTradingPersistsRiskRejectionAndCreatesNoOrderOrJob() {
        arrangeAccount(false, new BigDecimal("10000"));
        when(intentRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            InvestmentTradeIntentPo intent = invocation.getArgument(0);
            intent.setId(31L);
            return intent;
        });

        var result = service.accept(11L, command("rejected", "1", "100"),
                PaperTradingFacadeTests.snapshot());

        assertThat(result.intentStatus()).isEqualTo("RISK_REJECTED");
        assertThat(result.riskResults()).filteredOn(value -> !value.passed())
                .extracting(value -> value.ruleCode())
                .contains(InvestmentRiskRuleCode.TRADING_SWITCH_ENABLED);
        verify(orderRepository, never()).saveAndFlush(any());
        verify(enqueueService, never()).enqueue(any(InvestmentJobEnqueueCommand.class));
    }

    @Test
    void lockedPendingReservationsMakeTheConcurrentSecondIntentFailAvailableMargin() {
        arrangeAccount(true, new BigDecimal("80"));
        InvestmentPaperOrderPo reserved = new InvestmentPaperOrderPo();
        reserved.setId(40L);
        reserved.setIntentId(30L);
        reserved.setInstrumentId(502L);
        reserved.setQuantity(new BigDecimal("8"));
        reserved.setLeverage(new BigDecimal("10"));
        InvestmentTradeIntentPo reservedIntent = new InvestmentTradeIntentPo();
        reservedIntent.setId(30L);
        reservedIntent.setRequestedNotional(new BigDecimal("600"));
        when(orderRepository.findPendingOpeningByAccountIdForUpdate(21L)).thenReturn(List.of(reserved));
        when(intentRepository.findAllById(List.of(30L))).thenReturn(List.of(reservedIntent));
        when(intentRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            InvestmentTradeIntentPo intent = invocation.getArgument(0);
            intent.setId(32L);
            return intent;
        });

        var result = service.accept(11L, command("concurrent", "3", "300"),
                PaperTradingFacadeTests.snapshot());

        assertThat(result.intentStatus()).isEqualTo("RISK_REJECTED");
        assertThat(result.riskResults()).filteredOn(
                        value -> value.ruleCode() == InvestmentRiskRuleCode.AVAILABLE_MARGIN_LIMIT)
                .singleElement().satisfies(value -> {
                    assertThat(value.passed()).isFalse();
                    assertThat(value.observedValue()).isEqualByComparingTo("30");
                    assertThat(value.limitValue()).isEqualByComparingTo("20");
                });
        verify(orderRepository, never()).saveAndFlush(any());
        verify(enqueueService, never()).enqueue(any());
    }

    private void arrangeAccount(boolean tradingEnabled, BigDecimal wallet) {
        InvestmentPaperAccountPo account = new InvestmentPaperAccountPo();
        account.setId(21L);
        account.setWorkspaceId(11L);
        account.setWalletBalance(wallet);
        account.setTradingEnabled(tradingEnabled);
        account.setAgentAutoTradeEnabled(true);
        when(accountRepository.findByIdAndWorkspaceIdForUpdate(21L, 11L)).thenReturn(Optional.of(account));
        when(positionRepository.findByAccountIdForUpdate(21L)).thenReturn(List.of());
        when(orderRepository.findPendingOpeningByAccountIdForUpdate(21L)).thenReturn(List.of());
        when(riskProfileRepository.findByAccountId(21L)).thenReturn(Optional.of(profile()));
        when(ledgerRepository.sumDailyLoss(any(), any())).thenReturn(BigDecimal.ZERO);
    }

    private static InvestmentRiskProfilePo profile() {
        InvestmentRiskProfilePo profile = new InvestmentRiskProfilePo();
        profile.setAccountId(21L);
        profile.setMaxLeverage(new BigDecimal("10"));
        profile.setMaxOrderNotional(new BigDecimal("1000"));
        profile.setMaxPositionNotional(new BigDecimal("5000"));
        profile.setMaxGrossExposureNotional(new BigDecimal("10000"));
        profile.setMaxOpenPositions(5L);
        profile.setMaxDailyLossAmount(new BigDecimal("500"));
        profile.setMaxDrawdownRatio(new BigDecimal("0.20"));
        profile.setMaxOrdersPerHour(20L);
        profile.setCooldownSeconds(0L);
        profile.setMaxMarketDataAgeSeconds(60L);
        profile.setMaxSlippageBps(new BigDecimal("25"));
        return profile;
    }

    private static PaperTradeCommand command(String key, String quantity, String requestedNotional) {
        return new PaperTradeCommand(
                101L, 21L, 501L, InvestmentRiskSource.AGENT, "run-1", key,
                PositionAction.OPEN, PositionSide.LONG, OrderType.MARKET,
                new BigDecimal(quantity), new BigDecimal(requestedNotional), new BigDecimal("10"),
                null, false, "test", NOW, null, null);
    }
}
