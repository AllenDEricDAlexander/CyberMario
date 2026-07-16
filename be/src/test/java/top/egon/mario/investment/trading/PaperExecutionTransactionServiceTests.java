package top.egon.mario.investment.trading;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import top.egon.mario.investment.trading.matching.PaperMatchJobInput;
import top.egon.mario.investment.trading.matching.model.LiquidityRole;
import top.egon.mario.investment.trading.matching.model.MatchResult;
import top.egon.mario.investment.trading.matching.model.TradeSide;
import top.egon.mario.investment.trading.po.InvestmentMarginLedgerPo;
import top.egon.mario.investment.trading.po.InvestmentPaperFillPo;
import top.egon.mario.investment.trading.po.InvestmentPaperOrderPo;
import top.egon.mario.investment.trading.po.InvestmentTradeIntentPo;
import top.egon.mario.investment.trading.repository.InvestmentMarginLedgerRepository;
import top.egon.mario.investment.trading.repository.InvestmentPaperFillRepository;
import top.egon.mario.investment.trading.repository.InvestmentPaperOrderRepository;
import top.egon.mario.investment.trading.repository.InvestmentTradeIntentRepository;
import top.egon.mario.investment.trading.service.PaperExecutionTransactionService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaperExecutionTransactionServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-17T00:01:10Z");

    @Mock private InvestmentPaperAccountRepository accountRepository;
    @Mock private InvestmentPositionRepository positionRepository;
    @Mock private InvestmentPaperOrderRepository orderRepository;
    @Mock private InvestmentPaperFillRepository fillRepository;
    @Mock private InvestmentMarginLedgerRepository ledgerRepository;
    @Mock private InvestmentTradeIntentRepository intentRepository;

    private PaperExecutionTransactionService service;

    @BeforeEach
    void setUp() {
        service = new PaperExecutionTransactionService(
                accountRepository, positionRepository, orderRepository, fillRepository,
                ledgerRepository, intentRepository, new ObjectMapper());
    }

    @Test
    void openingFillAtomicallyWritesFeeMarginLedgerWalletPositionAndOrder() {
        InvestmentPaperAccountPo account = account("10000", 0L);
        InvestmentPaperOrderPo order = order("OPEN", "BUY", "5", "1");
        arrange(account, List.of(), order);
        when(fillRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            InvestmentPaperFillPo fill = invocation.getArgument(0);
            fill.setId(61L);
            return fill;
        });

        var result = service.execute(input(), match(TradeSide.BUY, "100", "1", "100", "0.06"), NOW);

        assertThat(result.orderStatus()).isEqualTo("FILLED");
        assertThat(result.idempotent()).isFalse();
        assertThat(account.getWalletBalance()).isEqualByComparingTo("9999.94");
        assertThat(account.getLedgerSequence()).isEqualTo(2L);
        assertThat(order.getRemainingQuantity()).isZero();
        assertThat(order.getMatchedAt()).isEqualTo(NOW);

        ArgumentCaptor<InvestmentPositionPo> position = ArgumentCaptor.forClass(InvestmentPositionPo.class);
        verify(positionRepository).saveAndFlush(position.capture());
        assertThat(position.getValue().getPositionSide()).isEqualTo("LONG");
        assertThat(position.getValue().getQuantity()).isEqualByComparingTo("1");
        assertThat(position.getValue().getIsolatedMargin()).isEqualByComparingTo("20");

        ArgumentCaptor<InvestmentMarginLedgerPo> ledger = ArgumentCaptor.forClass(InvestmentMarginLedgerPo.class);
        verify(ledgerRepository, times(2)).saveAndFlush(ledger.capture());
        assertThat(ledger.getAllValues()).extracting(InvestmentMarginLedgerPo::getEventType)
                .containsExactly("FEE", "MARGIN_RESERVE");
        assertThat(ledger.getAllValues()).extracting(InvestmentMarginLedgerPo::getSequenceNo)
                .containsExactly(1L, 2L);
        verify(fillRepository).saveAndFlush(any(InvestmentPaperFillPo.class));
        verify(accountRepository).saveAndFlush(account);
        verify(orderRepository).saveAndFlush(order);
    }

    @Test
    void closingFillRealizesPnlReleasesMarginAndDeletesAFlatPosition() {
        InvestmentPaperAccountPo account = account("1000", 7L);
        InvestmentPaperOrderPo order = order("CLOSE", "SELL", "5", "1");
        InvestmentPositionPo position = new InvestmentPositionPo();
        position.setId(71L);
        position.setAccountId(21L);
        position.setInstrumentId(501L);
        position.setPositionSide("LONG");
        position.setQuantity(new BigDecimal("1"));
        position.setEntryPrice(new BigDecimal("90"));
        position.setLeverage(new BigDecimal("5"));
        position.setIsolatedMargin(new BigDecimal("18"));
        position.setMaintenanceMarginRate(new BigDecimal("0.005"));
        position.setMaintenanceMargin(new BigDecimal("0.45"));
        position.setLiquidationPrice(new BigDecimal("72"));
        position.setRealizedPnl(BigDecimal.ZERO);
        position.setFundingPnl(BigDecimal.ZERO);
        arrange(account, List.of(position), order);
        when(fillRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.execute(input(), match(TradeSide.SELL, "100", "1", "100", "0.06"), NOW);

        assertThat(account.getWalletBalance()).isEqualByComparingTo("1009.94");
        assertThat(account.getLedgerSequence()).isEqualTo(10L);
        verify(positionRepository).delete(position);
        verify(positionRepository).flush();
        ArgumentCaptor<InvestmentMarginLedgerPo> ledger = ArgumentCaptor.forClass(InvestmentMarginLedgerPo.class);
        verify(ledgerRepository, times(3)).saveAndFlush(ledger.capture());
        assertThat(ledger.getAllValues()).extracting(InvestmentMarginLedgerPo::getEventType)
                .containsExactly("REALIZED_PNL", "FEE", "MARGIN_RELEASE");
        assertThat(ledger.getAllValues().getFirst().getAmount()).isEqualByComparingTo("10");
    }

    @Test
    void retryReturnsTheExistingFillWithoutASecondFinancialEffect() {
        InvestmentPaperAccountPo account = account("10000", 2L);
        InvestmentPaperOrderPo order = order("OPEN", "BUY", "5", "1");
        order.setStatus("FILLED");
        InvestmentPaperFillPo fill = new InvestmentPaperFillPo();
        fill.setId(61L);
        fill.setOrderId(41L);
        fill.setFillNo(1L);
        fill.setFillPrice(new BigDecimal("100"));
        fill.setQuantity(BigDecimal.ONE);
        fill.setFeeAmount(new BigDecimal("0.06"));
        fill.setFilledAt(NOW);
        arrange(account, List.of(), order);
        when(fillRepository.findByOrderIdAndFillNo(41L, 1L)).thenReturn(Optional.of(fill));

        var result = service.execute(input(), match(TradeSide.BUY, "100", "1", "100", "0.06"), NOW);

        assertThat(result.idempotent()).isTrue();
        assertThat(result.fill().fillId()).isEqualTo(61L);
        verify(ledgerRepository, never()).saveAndFlush(any());
        verify(fillRepository, never()).saveAndFlush(any());
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void executionRejectsAnOpeningOrderWhenActualMarginNoLongerFitsTheLockedAccount() {
        InvestmentPaperAccountPo account = account("100", 0L);
        InvestmentPaperOrderPo order = order("OPEN", "BUY", "5", "1");
        order.setIntentId(31L);
        InvestmentPaperOrderPo other = order("OPEN", "BUY", "5", "1");
        other.setId(42L);
        other.setIntentId(32L);
        InvestmentTradeIntentPo intent = new InvestmentTradeIntentPo();
        intent.setId(32L);
        intent.setRequestedNotional(new BigDecimal("300"));
        arrange(account, List.of(), order);
        when(orderRepository.findPendingOpeningByAccountIdForUpdate(21L)).thenReturn(List.of(order, other));
        when(intentRepository.findAllById(List.of(32L))).thenReturn(List.of(intent));

        var result = service.execute(input(), match(TradeSide.BUY, "250", "1", "250", "0.15"), NOW);

        assertThat(result.orderStatus()).isEqualTo("REJECTED");
        assertThat(order.getRejectionCode()).isEqualTo("AVAILABLE_MARGIN_LIMIT");
        verify(orderRepository).saveAndFlush(order);
        verify(positionRepository, never()).saveAndFlush(any());
        verify(fillRepository, never()).saveAndFlush(any());
        verify(ledgerRepository, never()).saveAndFlush(any());
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void cancellationWinningTheOrderLockRacePreventsEveryFillEffect() {
        InvestmentPaperAccountPo account = account("10000", 2L);
        InvestmentPaperOrderPo order = order("OPEN", "BUY", "5", "1");
        order.setStatus("CANCELLED");
        arrange(account, List.of(), order);

        var result = service.execute(input(), match(TradeSide.BUY, "100", "1", "100", "0.06"), NOW);

        assertThat(result.orderStatus()).isEqualTo("CANCELLED");
        assertThat(result.idempotent()).isTrue();
        verify(positionRepository, never()).saveAndFlush(any());
        verify(fillRepository, never()).saveAndFlush(any());
        verify(ledgerRepository, never()).saveAndFlush(any());
        verify(accountRepository, never()).saveAndFlush(any());
        verify(orderRepository, never()).saveAndFlush(any());
    }

    private void arrange(
            InvestmentPaperAccountPo account, List<InvestmentPositionPo> positions, InvestmentPaperOrderPo order) {
        when(accountRepository.findByIdAndWorkspaceIdForUpdate(21L, 11L)).thenReturn(Optional.of(account));
        when(positionRepository.findByAccountIdForUpdate(21L)).thenReturn(positions);
        when(orderRepository.findPendingOpeningByAccountIdForUpdate(21L)).thenReturn(List.of());
        when(orderRepository.findByScopeForUpdate(41L, 21L, 501L)).thenReturn(Optional.of(order));
    }

    private static InvestmentPaperAccountPo account(String wallet, Long sequence) {
        InvestmentPaperAccountPo account = new InvestmentPaperAccountPo();
        account.setId(21L);
        account.setWorkspaceId(11L);
        account.setWalletBalance(new BigDecimal(wallet));
        account.setLedgerSequence(sequence);
        return account;
    }

    private static InvestmentPaperOrderPo order(
            String action, String side, String leverage, String quantity) {
        InvestmentPaperOrderPo order = new InvestmentPaperOrderPo();
        order.setId(41L);
        order.setWorkspaceId(11L);
        order.setAccountId(21L);
        order.setInstrumentId(501L);
        order.setPositionAction(action);
        order.setSide(side);
        order.setLeverage(new BigDecimal(leverage));
        order.setQuantity(new BigDecimal(quantity));
        order.setRemainingQuantity(new BigDecimal(quantity));
        order.setStatus("PENDING_MATCH");
        return order;
    }

    private static PaperMatchJobInput input() {
        return new PaperMatchJobInput(
                41L, 11L, 21L, 501L, 1L, NOW.minusSeconds(70),
                new BigDecimal("0.1"), new BigDecimal("0.001"), BigDecimal.ONE,
                new BigDecimal("0.0002"), new BigDecimal("0.0006"),
                new BigDecimal("5"), new BigDecimal("0.005"));
    }

    private static MatchResult match(
            TradeSide side, String price, String quantity, String notional, String fee) {
        return MatchResult.filled(
                new top.egon.mario.investment.trading.matching.model.MatchingOrder(
                        41L, top.egon.mario.investment.common.model.OrderType.MARKET,
                        side == TradeSide.BUY ? top.egon.mario.investment.common.model.PositionSide.LONG
                                : top.egon.mario.investment.common.model.PositionSide.LONG,
                        side == TradeSide.BUY ? top.egon.mario.investment.common.model.PositionAction.OPEN
                                : top.egon.mario.investment.common.model.PositionAction.CLOSE,
                        new BigDecimal(quantity), null, NOW.minusSeconds(70)),
                NOW.minusSeconds(60), side, LiquidityRole.TAKER,
                new BigDecimal(price), new BigDecimal(quantity),
                new BigDecimal(notional), new BigDecimal(fee));
    }
}
