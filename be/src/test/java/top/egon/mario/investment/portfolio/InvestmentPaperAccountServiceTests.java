package top.egon.mario.investment.portfolio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.access.InvestmentAccessService;
import top.egon.mario.investment.portfolio.po.InvestmentPaperAccountPo;
import top.egon.mario.investment.portfolio.po.InvestmentRiskProfilePo;
import top.egon.mario.investment.portfolio.repository.InvestmentPaperAccountRepository;
import top.egon.mario.investment.portfolio.repository.InvestmentRiskProfileRepository;
import top.egon.mario.investment.portfolio.service.InvestmentPaperAccountService;
import top.egon.mario.investment.portfolio.web.dto.CreateInvestmentPaperAccountRequest;
import top.egon.mario.investment.portfolio.web.dto.InvestmentRiskProfileRequest;
import top.egon.mario.investment.portfolio.web.dto.UpdateInvestmentRiskProfileRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestmentPaperAccountServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");

    @Mock
    private InvestmentPaperAccountRepository accountRepository;
    @Mock
    private InvestmentRiskProfileRepository riskProfileRepository;
    @Mock
    private InvestmentAccessService accessService;

    private InvestmentPaperAccountService service;

    @BeforeEach
    void setUp() {
        service = new InvestmentPaperAccountService(
                accountRepository, riskProfileRepository, accessService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsAccountAndRequiredRiskProfileAtomicallyWithBothSwitchesForcedOff() {
        when(accountRepository.findByWorkspaceIdAndName(11L, "Main paper")).thenReturn(Optional.empty());
        when(accountRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            InvestmentPaperAccountPo account = invocation.getArgument(0);
            account.setId(21L);
            account.setVersion(0L);
            return account;
        });
        when(riskProfileRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            InvestmentRiskProfilePo profile = invocation.getArgument(0);
            profile.setId(31L);
            profile.setVersion(0L);
            return profile;
        });

        var result = service.create(101L, 11L, new CreateInvestmentPaperAccountRequest(
                " Main paper ", "10000.000000000000000000", true, true, riskProfile()));

        assertThat(result.account().id()).isEqualTo(21L);
        assertThat(result.account().baseCurrency()).isEqualTo("USDT");
        assertThat(result.account().initialEquity()).isEqualTo("10000.000000000000000000");
        assertThat(result.account().equity()).isEqualTo("10000.000000000000000000");
        assertThat(result.account().usedMargin()).isEqualTo("0");
        assertThat(result.account().tradingEnabled()).isFalse();
        assertThat(result.account().agentAutoTradeEnabled()).isFalse();
        assertThat(result.riskProfile().maxOrderNotional()).isEqualTo("1000");
        verify(accessService).requireWorkspaceOwner(11L, 101L);
        verify(accountRepository).saveAndFlush(any(InvestmentPaperAccountPo.class));
        verify(riskProfileRepository).saveAndFlush(any(InvestmentRiskProfilePo.class));
    }

    @Test
    void rejectsCreateWithoutAnExplicitRiskProfileBeforeAnyWrite() {
        assertThatThrownBy(() -> service.create(101L, 11L,
                new CreateInvestmentPaperAccountRequest("Main", "10000", false, false, null)))
                .isInstanceOf(InvestmentException.class)
                .satisfies(error -> assertThat(((InvestmentException) error).getErrorCode())
                        .isEqualTo(InvestmentErrorCode.INVALID_REQUEST));

        verify(accountRepository, never()).saveAndFlush(any());
        verify(riskProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void detailLookupCannotCrossTheWorkspaceOwnerBoundary() {
        when(accountRepository.findOwnedAccount(21L, 202L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(202L, 21L))
                .isInstanceOf(InvestmentException.class)
                .satisfies(error -> assertThat(((InvestmentException) error).getErrorCode())
                        .isEqualTo(InvestmentErrorCode.FORBIDDEN));

        verify(accountRepository).findOwnedAccount(21L, 202L);
        verify(riskProfileRepository, never()).findByAccountId(any());
    }

    @Test
    void riskUpdateRejectsStaleVersionAndInvalidWidenedShape() {
        InvestmentPaperAccountPo account = account(21L, 11L);
        InvestmentRiskProfilePo profile = profile(31L, 21L, 4L);
        when(accountRepository.findOwnedAccount(21L, 101L)).thenReturn(Optional.of(account));
        when(riskProfileRepository.findByAccountId(21L)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.updateRiskProfile(
                101L, 21L, updateRequest(3L, riskProfile())))
                .isInstanceOf(InvestmentException.class)
                .satisfies(error -> assertThat(((InvestmentException) error).getErrorCode())
                        .isEqualTo(InvestmentErrorCode.CONFLICT));

        InvestmentRiskProfileRequest invalid = new InvestmentRiskProfileRequest(
                "10", "2000", "1000", "5000", 5L, "500", "0.20",
                20L, 30L, 60L, "25");
        assertThatThrownBy(() -> service.updateRiskProfile(
                101L, 21L, updateRequest(4L, invalid)))
                .isInstanceOf(InvestmentException.class)
                .satisfies(error -> assertThat(((InvestmentException) error).getErrorCode())
                        .isEqualTo(InvestmentErrorCode.INVALID_REQUEST));

        verify(riskProfileRepository, never()).saveAndFlush(any());
    }

    private static InvestmentRiskProfileRequest riskProfile() {
        return new InvestmentRiskProfileRequest(
                "10", "1000", "5000", "10000", 5L, "500", "0.20",
                20L, 30L, 60L, "25");
    }

    private static UpdateInvestmentRiskProfileRequest updateRequest(
            Long version, InvestmentRiskProfileRequest profile) {
        return new UpdateInvestmentRiskProfileRequest(
                version, profile.maxLeverage(), profile.maxOrderNotional(), profile.maxPositionNotional(),
                profile.maxGrossExposureNotional(), profile.maxOpenPositions(), profile.maxDailyLossAmount(),
                profile.maxDrawdownRatio(), profile.maxOrdersPerHour(), profile.cooldownSeconds(),
                profile.maxMarketDataAgeSeconds(), profile.maxSlippageBps());
    }

    private static InvestmentPaperAccountPo account(Long id, Long workspaceId) {
        InvestmentPaperAccountPo account = new InvestmentPaperAccountPo();
        account.setId(id);
        account.setWorkspaceId(workspaceId);
        account.setName("Main");
        account.setInitialEquity(new java.math.BigDecimal("10000"));
        account.setWalletBalance(new java.math.BigDecimal("10000"));
        account.setOpenedAt(NOW);
        account.setVersion(0L);
        return account;
    }

    private static InvestmentRiskProfilePo profile(Long id, Long accountId, Long version) {
        InvestmentRiskProfilePo profile = new InvestmentRiskProfilePo();
        profile.setId(id);
        profile.setAccountId(accountId);
        profile.setVersion(version);
        return profile;
    }
}
