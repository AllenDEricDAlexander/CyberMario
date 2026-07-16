package top.egon.mario.investment.trading;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.egon.mario.investment.portfolio.po.InvestmentPaperAccountPo;
import top.egon.mario.investment.portfolio.po.InvestmentPositionPo;
import top.egon.mario.investment.portfolio.repository.InvestmentPaperAccountRepository;
import top.egon.mario.investment.portfolio.repository.InvestmentPositionRepository;
import top.egon.mario.investment.trading.po.InvestmentMarginLedgerPo;
import top.egon.mario.investment.trading.repository.InvestmentMarginLedgerRepository;
import top.egon.mario.investment.trading.service.PaperFundingSettlementService;
import top.egon.mario.investment.trading.service.model.PaperFundingJobInput;
import top.egon.mario.investment.trading.service.model.PaperFundingMarketSnapshot;

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
class PaperFundingSettlementTests {

    private static final Instant FUNDING_TIME = Instant.parse("2026-07-17T00:00:00Z");

    @Mock private InvestmentPaperAccountRepository accountRepository;
    @Mock private InvestmentPositionRepository positionRepository;
    @Mock private InvestmentMarginLedgerRepository ledgerRepository;

    private PaperFundingSettlementService service;

    @BeforeEach
    void setUp() {
        service = new PaperFundingSettlementService(
                accountRepository, positionRepository, ledgerRepository, new ObjectMapper());
    }

    @Test
    void positiveFundingDebitsLongAndCreditsShortWithDeterministicKeys() {
        assertSettlement("LONG", "-1", "99");
        org.mockito.Mockito.reset(accountRepository, positionRepository, ledgerRepository);
        assertSettlement("SHORT", "1", "101");
    }

    @Test
    void repeatedSettlementReturnsTheExistingLedgerWithoutAnotherFinancialEffect() {
        InvestmentPaperAccountPo account = account();
        InvestmentPositionPo position = position("LONG");
        arrange(account, position);
        InvestmentMarginLedgerPo existing = new InvestmentMarginLedgerPo();
        existing.setAmount(new BigDecimal("-1"));
        when(ledgerRepository.findByIdempotencyKey(PaperFundingSettlementService.idempotencyKey(input())))
                .thenReturn(Optional.of(existing));

        var result = service.settle(input(), market());

        assertThat(result.idempotent()).isTrue();
        assertThat(result.amount()).isEqualByComparingTo("-1");
        verify(ledgerRepository, never()).saveAndFlush(any());
        verify(accountRepository, never()).saveAndFlush(any());
        verify(positionRepository, never()).saveAndFlush(any());
    }

    private void assertSettlement(String side, String expectedAmount, String expectedBalance) {
        InvestmentPaperAccountPo account = account();
        InvestmentPositionPo position = position(side);
        arrange(account, position);

        var result = service.settle(input(), market());

        assertThat(result.amount()).isEqualByComparingTo(expectedAmount);
        assertThat(account.getWalletBalance()).isEqualByComparingTo(expectedBalance);
        assertThat(position.getFundingPnl()).isEqualByComparingTo(expectedAmount);
        verify(ledgerRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(
                value -> value.getIdempotencyKey().equals(
                        "funding:21:71:501:" + FUNDING_TIME.toEpochMilli())
                        && value.getAmount().compareTo(new BigDecimal(expectedAmount)) == 0));
    }

    private void arrange(InvestmentPaperAccountPo account, InvestmentPositionPo position) {
        when(accountRepository.findByIdAndWorkspaceIdForUpdate(21L, 11L)).thenReturn(Optional.of(account));
        when(positionRepository.findByAccountIdForUpdate(21L)).thenReturn(List.of(position));
    }

    private static InvestmentPaperAccountPo account() {
        InvestmentPaperAccountPo account = new InvestmentPaperAccountPo();
        account.setId(21L);
        account.setWorkspaceId(11L);
        account.setWalletBalance(new BigDecimal("100"));
        account.setLedgerSequence(4L);
        return account;
    }

    private static InvestmentPositionPo position(String side) {
        InvestmentPositionPo position = new InvestmentPositionPo();
        position.setId(71L);
        position.setAccountId(21L);
        position.setInstrumentId(501L);
        position.setPositionSide(side);
        position.setQuantity(new BigDecimal("10"));
        position.setFundingPnl(BigDecimal.ZERO);
        return position;
    }

    private static PaperFundingJobInput input() {
        return new PaperFundingJobInput(11L, 21L, 71L, 501L, 1L, FUNDING_TIME);
    }

    private static PaperFundingMarketSnapshot market() {
        return new PaperFundingMarketSnapshot(
                new BigDecimal("100"), new BigDecimal("0.001"), 2L,
                BigDecimal.ONE, FUNDING_TIME);
    }
}
