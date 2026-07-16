package top.egon.mario.investment.quant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.investment.quant.backtest.InvestmentBacktestService;
import top.egon.mario.investment.quant.web.InvestmentBacktestController;
import top.egon.mario.investment.quant.web.dto.InvestmentBacktestRunResponse;
import top.egon.mario.investment.quant.web.dto.SubmitInvestmentBacktestRequest;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvestmentBacktestControllerTests {

    private InvestmentBacktestService service;
    private InvestmentBacktestController controller;
    private RbacPrincipal principal;

    @BeforeEach
    void setUp() {
        service = mock(InvestmentBacktestService.class);
        controller = new InvestmentBacktestController(service);
        ReflectionTestUtils.setField(controller, "blockingScheduler", Schedulers.immediate());
        principal = new RbacPrincipal(5L, "owner", Set.of("INVESTMENT_USER"), Set.of(), "v1");
    }

    @Test
    void submitUsesAuthenticatedActorAndWorkspacePath() {
        SubmitInvestmentBacktestRequest request = new SubmitInvestmentBacktestRequest();
        InvestmentBacktestRunResponse run = run();
        when(service.submit(5L, 7L, request)).thenReturn(run);

        StepVerifier.create(controller.submit(7L, request, principal))
                .assertNext(response -> assertThat(response.data()).isEqualTo(run))
                .verifyComplete();

        verify(service).submit(5L, 7L, request);
    }

    @Test
    void listUsesStableOneBasedPageEnvelope() {
        when(service.list(org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.eq(7L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(run())));

        StepVerifier.create(controller.list(7L, 1, 20, principal))
                .assertNext(response -> {
                    assertThat(response.data().records()).hasSize(1);
                    assertThat(response.data().page()).isEqualTo(1);
                    assertThat(response.data().size()).isEqualTo(1);
                }).verifyComplete();
    }

    @Test
    void detailUsesAuthenticatedActorAndRunIdWithoutAClientWorkspace() {
        when(service.detail(5L, 51L)).thenReturn(run());

        StepVerifier.create(controller.detail(51L, principal))
                .assertNext(response -> assertThat(response.data().runId()).isEqualTo(51L))
                .verifyComplete();

        verify(service).detail(5L, 51L);
    }

    private InvestmentBacktestRunResponse run() {
        return new InvestmentBacktestRunResponse(51L, 7L, 61L, 31L, 41L, "QUEUED", "10000",
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, Instant.parse("2035-01-01T00:00:00Z"));
    }
}
