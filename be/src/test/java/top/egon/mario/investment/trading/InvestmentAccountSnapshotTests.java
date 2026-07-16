package top.egon.mario.investment.trading;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.egon.mario.investment.portfolio.po.InvestmentAccountSnapshotId;
import top.egon.mario.investment.portfolio.po.InvestmentAccountSnapshotPo;
import top.egon.mario.investment.portfolio.po.InvestmentPaperAccountPo;
import top.egon.mario.investment.portfolio.po.InvestmentPositionPo;
import top.egon.mario.investment.portfolio.repository.InvestmentAccountSnapshotRepository;
import top.egon.mario.investment.portfolio.repository.InvestmentPaperAccountRepository;
import top.egon.mario.investment.portfolio.repository.InvestmentPositionRepository;
import top.egon.mario.investment.trading.service.InvestmentAccountSnapshotService;
import top.egon.mario.investment.trading.service.model.PositionMarkSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestmentAccountSnapshotTests {

    private static final Instant NOW = Instant.parse("2026-07-17T00:05:00Z");

    @Mock private InvestmentPaperAccountRepository accountRepository;
    @Mock private InvestmentPositionRepository positionRepository;
    @Mock private InvestmentAccountSnapshotRepository snapshotRepository;

    private InvestmentAccountSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new InvestmentAccountSnapshotService(
                accountRepository, positionRepository, snapshotRepository);
    }

    @Test
    void exactEquityReturnAndDrawdownUseTheFrozenMarkAndHistoricalPeak() {
        InvestmentPaperAccountPo account = new InvestmentPaperAccountPo();
        account.setId(21L);
        account.setWorkspaceId(11L);
        account.setWalletBalance(new BigDecimal("100"));
        account.setInitialEquity(new BigDecimal("100"));
        InvestmentPositionPo position = new InvestmentPositionPo();
        position.setAccountId(21L);
        position.setInstrumentId(501L);
        position.setPositionSide("LONG");
        position.setQuantity(new BigDecimal("2"));
        position.setEntryPrice(new BigDecimal("90"));
        position.setIsolatedMargin(new BigDecimal("20"));
        position.setMaintenanceMargin(new BigDecimal("1"));
        InvestmentAccountSnapshotPo peak = new InvestmentAccountSnapshotPo();
        peak.setId(new InvestmentAccountSnapshotId(21L, NOW.minusSeconds(60)));
        peak.setEquity(new BigDecimal("150"));
        when(accountRepository.findByIdAndWorkspaceIdForUpdate(21L, 11L)).thenReturn(Optional.of(account));
        when(positionRepository.findByAccountIdForUpdate(21L)).thenReturn(List.of(position));
        when(snapshotRepository.findFirstByIdAccountIdOrderByEquityDescIdSnapshotTimeAsc(21L))
                .thenReturn(Optional.of(peak));

        var result = service.capture(11L, 21L, NOW,
                Map.of(501L, new PositionMarkSnapshot(501L, new BigDecimal("100"), BigDecimal.ONE)));

        assertThat(result.unrealizedPnl()).isEqualByComparingTo("20");
        assertThat(result.equity()).isEqualByComparingTo("120");
        assertThat(result.availableBalance()).isEqualByComparingTo("100");
        assertThat(result.grossExposure()).isEqualByComparingTo("200");
        assertThat(result.totalReturn()).isEqualByComparingTo("0.2");
        assertThat(result.drawdown()).isEqualByComparingTo("0.2");
        assertThat(result.positionCount()).isEqualTo(1L);
    }
}
