package top.egon.mario.investment.trading;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.egon.mario.investment.portfolio.po.InvestmentPaperAccountPo;
import top.egon.mario.investment.portfolio.po.InvestmentPositionPo;
import top.egon.mario.investment.portfolio.repository.InvestmentPaperAccountRepository;
import top.egon.mario.investment.portfolio.repository.InvestmentPositionRepository;
import top.egon.mario.investment.trading.matching.model.MatchResult;
import top.egon.mario.investment.trading.po.InvestmentPaperOrderPo;
import top.egon.mario.investment.trading.po.InvestmentTradeIntentPo;
import top.egon.mario.investment.trading.repository.InvestmentPaperOrderRepository;
import top.egon.mario.investment.trading.repository.InvestmentTradeIntentRepository;
import top.egon.mario.investment.trading.service.PaperExecutionTransactionService;
import top.egon.mario.investment.trading.service.PaperLiquidationService;
import top.egon.mario.investment.trading.service.PaperOrderService;
import top.egon.mario.investment.trading.service.model.PaperExecutionResult;
import top.egon.mario.investment.trading.service.model.PaperMaintenanceMarketSnapshot;
import top.egon.mario.investment.trading.service.model.PaperMarginCheckJobInput;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaperLiquidationTests {

    private static final Instant NOW = Instant.parse("2026-07-17T00:05:00Z");

    @Mock private InvestmentPaperAccountRepository accountRepository;
    @Mock private InvestmentPositionRepository positionRepository;
    @Mock private InvestmentPaperOrderRepository orderRepository;
    @Mock private InvestmentTradeIntentRepository intentRepository;
    @Mock private PaperOrderService orderService;
    @Mock private PaperExecutionTransactionService executionService;

    private PaperLiquidationService service;

    @BeforeEach
    void setUp() {
        service = new PaperLiquidationService(
                accountRepository, positionRepository, orderRepository, intentRepository,
                orderService, executionService);
    }

    @Test
    void liquidationCancelsOpeningRiskAndCreatesConservativeSystemFacts() {
        InvestmentPositionPo position = new InvestmentPositionPo();
        position.setId(71L);
        position.setAccountId(21L);
        position.setInstrumentId(501L);
        position.setPositionSide("LONG");
        position.setQuantity(BigDecimal.ONE);
        position.setLeverage(new BigDecimal("10"));
        InvestmentPaperOrderPo opening = pending(40L, false);
        InvestmentPaperOrderPo reduceOnly = pending(42L, true);
        when(accountRepository.findByIdAndWorkspaceIdForUpdate(21L, 11L))
                .thenReturn(Optional.of(new InvestmentPaperAccountPo()));
        when(positionRepository.findByAccountIdForUpdate(21L)).thenReturn(List.of(position));
        when(orderRepository.findPendingByAccountAndInstrumentForUpdate(21L, 501L))
                .thenReturn(List.of(opening, reduceOnly));
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
        when(executionService.execute(any(), any(), any()))
                .thenReturn(new PaperExecutionResult(41L, "FILLED", null, false));

        var result = service.liquidate(input(), market(), NOW);

        assertThat(result.status()).isEqualTo("FILLED");
        assertThat(opening.getStatus()).isEqualTo("CANCELLED");
        assertThat(reduceOnly.getStatus()).isEqualTo("CANCELLED");
        verify(intentRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(
                value -> value.getSourceType().equals("SYSTEM") && value.getPositionAction().equals("CLOSE")));
        verify(orderRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(
                value -> value.getOrigin().equals("LIQUIDATION") && value.isReduceOnly()));
        ArgumentCaptor<MatchResult> match = ArgumentCaptor.forClass(MatchResult.class);
        verify(executionService).execute(any(), match.capture(), org.mockito.ArgumentMatchers.eq(NOW));
        assertThat(match.getValue().fillPrice()).isEqualByComparingTo("99.9");
    }

    private static InvestmentPaperOrderPo pending(long id, boolean reduceOnly) {
        InvestmentPaperOrderPo order = new InvestmentPaperOrderPo();
        order.setId(id);
        order.setStatus("PENDING_MATCH");
        order.setReduceOnly(reduceOnly);
        return order;
    }

    private static PaperMarginCheckJobInput input() {
        return new PaperMarginCheckJobInput(11L, 21L, 71L, 501L, 1L, NOW);
    }

    private static PaperMaintenanceMarketSnapshot market() {
        return new PaperMaintenanceMarketSnapshot(
                new BigDecimal("100"), NOW, 2L, new BigDecimal("0.1"),
                new BigDecimal("0.001"), BigDecimal.ONE, new BigDecimal("0.0006"),
                new BigDecimal("10"), new BigDecimal("0.005"), NOW.minusSeconds(60));
    }
}
