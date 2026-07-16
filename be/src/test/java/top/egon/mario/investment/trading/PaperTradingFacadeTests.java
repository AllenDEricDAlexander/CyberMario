package top.egon.mario.investment.trading;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.egon.mario.investment.common.model.OrderType;
import top.egon.mario.investment.common.model.PositionAction;
import top.egon.mario.investment.common.model.PositionSide;
import top.egon.mario.investment.portfolio.po.InvestmentPaperAccountPo;
import top.egon.mario.investment.portfolio.repository.InvestmentPaperAccountRepository;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskSource;
import top.egon.mario.investment.trading.matching.PaperMarketSnapshotReader;
import top.egon.mario.investment.trading.service.PaperIntentAcceptanceTransactionService;
import top.egon.mario.investment.trading.service.PaperOrderService;
import top.egon.mario.investment.trading.service.PaperTradingFacade;
import top.egon.mario.investment.trading.service.model.PaperAcceptanceMarketSnapshot;
import top.egon.mario.investment.trading.service.model.PaperTradeCommand;
import top.egon.mario.investment.trading.service.model.PaperTradeResult;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaperTradingFacadeTests {

    private static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");

    @Mock
    private InvestmentPaperAccountRepository accountRepository;
    @Mock
    private PaperOrderService orderService;
    @Mock
    private PaperMarketSnapshotReader snapshotReader;
    @Mock
    private PaperIntentAcceptanceTransactionService acceptanceService;

    private PaperTradingFacade facade;

    @BeforeEach
    void setUp() {
        facade = new PaperTradingFacade(accountRepository, orderService, snapshotReader, acceptanceService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void userStrategyAndAgentSourcesAllEnterTheSameAcceptanceService() {
        InvestmentPaperAccountPo account = new InvestmentPaperAccountPo();
        account.setId(21L);
        account.setWorkspaceId(11L);
        when(accountRepository.findOwnedAccount(21L, 101L)).thenReturn(Optional.of(account));
        PaperAcceptanceMarketSnapshot snapshot = snapshot();
        when(snapshotReader.read(501L, bd("1"), NOW)).thenReturn(snapshot);

        for (InvestmentRiskSource source : List.of(
                InvestmentRiskSource.USER, InvestmentRiskSource.STRATEGY, InvestmentRiskSource.AGENT)) {
            PaperTradeCommand command = command(source, "intent-" + source.name());
            PaperTradeResult expected = new PaperTradeResult(
                    31L, "RISK_REJECTED", List.of(), null, null);
            when(acceptanceService.accept(11L, command, snapshot)).thenReturn(expected);

            assertThat(facade.submitIntent(command)).isSameAs(expected);
            verify(acceptanceService).accept(11L, command, snapshot);
        }
    }

    @Test
    void duplicateIdempotencyReturnsExistingFactWithoutReloadingMarketData() {
        PaperTradeCommand command = command(InvestmentRiskSource.AGENT, "same-key");
        PaperTradeResult existing = new PaperTradeResult(31L, "ACCEPTED", List.of(), null, null);
        InvestmentPaperAccountPo account = new InvestmentPaperAccountPo();
        account.setId(21L);
        account.setWorkspaceId(11L);
        when(accountRepository.findOwnedAccount(21L, 101L)).thenReturn(Optional.of(account));
        when(orderService.findByIdempotencyKey(21L, "same-key")).thenReturn(Optional.of(existing));

        assertThat(facade.submitIntent(command)).isSameAs(existing);

        verify(accountRepository).findOwnedAccount(21L, 101L);
        verify(snapshotReader, never()).read(501L, bd("1"), NOW);
        verify(acceptanceService, never()).accept(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void duplicateLookupCannotRunBeforeTheAccountOwnerBoundary() {
        PaperTradeCommand command = command(InvestmentRiskSource.USER, "guessed-key");
        when(accountRepository.findOwnedAccount(21L, 101L)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> facade.submitIntent(command))
                .isInstanceOf(top.egon.mario.investment.common.InvestmentException.class);

        verify(orderService, never()).findByIdempotencyKey(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
        verify(snapshotReader, never()).read(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private static PaperTradeCommand command(InvestmentRiskSource source, String key) {
        return new PaperTradeCommand(
                101L, 21L, 501L, source, "source-1", key,
                PositionAction.OPEN, PositionSide.LONG, OrderType.MARKET,
                bd("1"), bd("100"), bd("5"), null, false, "test", NOW, null, null);
    }

    static PaperAcceptanceMarketSnapshot snapshot() {
        return new PaperAcceptanceMarketSnapshot(
                1L, 501L, true, true, bd("100"), NOW.minusSeconds(5), 2L, 3L,
                bd("0.1"), bd("0.001"), bd("1"), bd("0.0002"), bd("0.0006"), bd("50"),
                NOW.minusSeconds(60), bd("20"), bd("0.005"), true, bd("5"));
    }

    static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
