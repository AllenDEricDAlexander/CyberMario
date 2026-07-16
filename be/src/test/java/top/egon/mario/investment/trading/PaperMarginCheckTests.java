package top.egon.mario.investment.trading;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.egon.mario.investment.portfolio.po.InvestmentPositionPo;
import top.egon.mario.investment.portfolio.repository.InvestmentPaperAccountRepository;
import top.egon.mario.investment.portfolio.repository.InvestmentPositionRepository;
import top.egon.mario.investment.trading.service.PaperLiquidationService;
import top.egon.mario.investment.trading.service.PaperMarginCheckService;
import top.egon.mario.investment.trading.service.model.PaperLiquidationResult;
import top.egon.mario.investment.trading.service.model.PaperMaintenanceMarketSnapshot;
import top.egon.mario.investment.trading.service.model.PaperMarginCheckJobInput;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaperMarginCheckTests {

    private static final Instant NOW = Instant.parse("2026-07-17T00:05:00Z");

    @Mock private InvestmentPaperAccountRepository accountRepository;
    @Mock private InvestmentPositionRepository positionRepository;
    @Mock private PaperLiquidationService liquidationService;

    private PaperMarginCheckService service;

    @BeforeEach
    void setUp() {
        service = new PaperMarginCheckService(accountRepository, positionRepository, liquidationService);
    }

    @Test
    void maintenanceThresholdEqualityLiquidatesConservatively() {
        InvestmentPositionPo position = position("0.56");
        arrange(position);
        when(liquidationService.liquidate(input(), market(), NOW))
                .thenReturn(new PaperLiquidationResult(21L, 71L, 41L, "FILLED", false));

        var result = service.check(input(), market(), NOW);

        assertThat(result.status()).isEqualTo("LIQUIDATED");
        assertThat(result.positionEquity()).isEqualByComparingTo("0.56");
        assertThat(result.liquidationThreshold()).isEqualByComparingTo("0.56");
        assertThat(result.liquidationOrderId()).isEqualTo(41L);
        verify(positionRepository, never()).saveAndFlush(any());
    }

    @Test
    void valueAboveTheThresholdStaysHealthyAndPersistsTheCheck() {
        InvestmentPositionPo position = position("0.57");
        arrange(position);

        var result = service.check(input(), market(), NOW);

        assertThat(result.status()).isEqualTo("HEALTHY");
        assertThat(position.getLastMarginCheckAt()).isEqualTo(NOW);
        verify(positionRepository).saveAndFlush(position);
        verify(liquidationService, never()).liquidate(any(), any(), any());
    }

    private void arrange(InvestmentPositionPo position) {
        when(accountRepository.findByIdAndWorkspaceIdForUpdate(21L, 11L))
                .thenReturn(Optional.of(new top.egon.mario.investment.portfolio.po.InvestmentPaperAccountPo()));
        when(positionRepository.findByAccountIdForUpdate(21L)).thenReturn(List.of(position));
    }

    private static InvestmentPositionPo position(String margin) {
        InvestmentPositionPo position = new InvestmentPositionPo();
        position.setId(71L);
        position.setAccountId(21L);
        position.setInstrumentId(501L);
        position.setPositionSide("LONG");
        position.setQuantity(BigDecimal.ONE);
        position.setEntryPrice(new BigDecimal("100"));
        position.setLeverage(new BigDecimal("10"));
        position.setIsolatedMargin(new BigDecimal(margin));
        return position;
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
