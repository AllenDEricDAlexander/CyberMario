package top.egon.mario.investment.portfolio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.investment.portfolio.service.InvestmentPaperAccountService;
import top.egon.mario.investment.portfolio.web.InvestmentPaperAccountController;
import top.egon.mario.investment.portfolio.web.dto.InvestmentRiskProfileRequest;
import top.egon.mario.investment.portfolio.web.dto.InvestmentRiskProfileResponse;
import top.egon.mario.investment.portfolio.web.dto.UpdateInvestmentRiskProfileRequest;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestmentRiskControllerTests {

    @Mock
    private InvestmentPaperAccountService accountService;

    private InvestmentPaperAccountController controller;
    private RbacPrincipal principal;

    @BeforeEach
    void setUp() {
        controller = new InvestmentPaperAccountController(accountService);
        ReflectionTestUtils.setField(controller, "blockingScheduler", Schedulers.immediate());
        principal = new RbacPrincipal(101L, "owner", Set.of("INVESTMENT_USER"), Set.of(), "v1");
    }

    @Test
    void readsRiskProfileOnlyThroughTheAuthenticatedActor() {
        InvestmentRiskProfileResponse response = response();
        when(accountService.getRiskProfile(101L, 21L)).thenReturn(response);

        StepVerifier.create(controller.getRiskProfile(21L, principal))
                .assertNext(api -> assertThat(api.data()).isEqualTo(response))
                .verifyComplete();

        verify(accountService).getRiskProfile(101L, 21L);
    }

    @Test
    void updatesRiskProfileWithVersionAndAuthenticatedActor() {
        InvestmentRiskProfileRequest profile = profile();
        UpdateInvestmentRiskProfileRequest request = new UpdateInvestmentRiskProfileRequest(
                4L, profile.maxLeverage(), profile.maxOrderNotional(), profile.maxPositionNotional(),
                profile.maxGrossExposureNotional(), profile.maxOpenPositions(), profile.maxDailyLossAmount(),
                profile.maxDrawdownRatio(), profile.maxOrdersPerHour(), profile.cooldownSeconds(),
                profile.maxMarketDataAgeSeconds(), profile.maxSlippageBps());
        InvestmentRiskProfileResponse response = response();
        when(accountService.updateRiskProfile(101L, 21L, request)).thenReturn(response);

        StepVerifier.create(controller.updateRiskProfile(21L, request, principal))
                .assertNext(api -> assertThat(api.data()).isEqualTo(response))
                .verifyComplete();

        verify(accountService).updateRiskProfile(101L, 21L, request);
    }

    private static InvestmentRiskProfileRequest profile() {
        return new InvestmentRiskProfileRequest(
                "10", "1000", "5000", "10000", 5L, "500", "0.20",
                20L, 30L, 60L, "25");
    }

    private static InvestmentRiskProfileResponse response() {
        return new InvestmentRiskProfileResponse(
                31L, 21L, "10", "1000", "5000", "10000", 5L, "500", "0.20",
                20L, 30L, 60L, "25", 4L);
    }
}
