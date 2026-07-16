package top.egon.mario.investment.portfolio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.investment.portfolio.query.InvestmentPortfolioQueryService;
import top.egon.mario.investment.portfolio.web.InvestmentPortfolioController;
import top.egon.mario.investment.portfolio.web.dto.InvestmentEquityResponse;
import top.egon.mario.investment.portfolio.web.dto.InvestmentFillMarkerResponse;
import top.egon.mario.investment.portfolio.web.dto.InvestmentLedgerResponse;
import top.egon.mario.investment.portfolio.web.dto.InvestmentPositionResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestmentPortfolioControllerTests {

    private static final Instant FROM = Instant.parse("2026-07-16T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-07-17T00:00:00Z");

    @Mock private InvestmentPortfolioQueryService queryService;

    private InvestmentPortfolioController controller;
    private RbacPrincipal principal;

    @BeforeEach
    void setUp() {
        controller = new InvestmentPortfolioController(queryService);
        ReflectionTestUtils.setField(controller, "blockingScheduler", Schedulers.immediate());
        principal = new RbacPrincipal(101L, "owner", Set.of("INVESTMENT_USER"), Set.of(), "v1");
    }

    @Test
    void everyPortfolioGetEndpointReturnsOwnerScopedDecimalStringProjections() {
        InvestmentFillMarkerResponse fill = new InvestmentFillMarkerResponse(
                61L, 501L, FROM, FROM.plusSeconds(60), "SELL", "CLOSE", "LIQUIDATION",
                "LIQUIDATION_FILL", "99.9", "1", true);
        when(queryService.fills(101L, 21L, 501L, FROM, TO, 0, 100))
                .thenReturn(new PageImpl<>(List.of(fill), PageRequest.of(0, 100), 1));
        InvestmentPositionResponse position = new InvestmentPositionResponse(
                71L, 501L, "LONG", "1", "100", "10", "101", "90", "10", "0.5",
                "1", "-0.1", "1", FROM, TO);
        when(queryService.positions(101L, 21L)).thenReturn(List.of(position));
        InvestmentLedgerResponse ledger = new InvestmentLedgerResponse(
                81L, 5L, "FUNDING", "-0.1", "99.9", 501L, "PAPER_FUNDING", "71", TO);
        when(queryService.ledger(101L, 21L, 0, 50))
                .thenReturn(new PageImpl<>(List.of(ledger), PageRequest.of(0, 50), 1));
        InvestmentEquityResponse equity = new InvestmentEquityResponse(
                TO, "99.9", "10", "0.5", "1", "100.9", "90.9", "101", "0.009", "0", 1L);
        when(queryService.equity(101L, 21L, 0, 100))
                .thenReturn(new PageImpl<>(List.of(equity), PageRequest.of(0, 100), 1));

        StepVerifier.create(controller.fills(21L, 501L, FROM, TO, 1, 100, principal))
                .assertNext(response -> assertThat(response.data().records().getFirst().liquidation()).isTrue())
                .verifyComplete();
        StepVerifier.create(controller.positions(21L, principal))
                .assertNext(response -> assertThat(response.data().getFirst().markPrice()).isEqualTo("101"))
                .verifyComplete();
        StepVerifier.create(controller.ledger(21L, 1, 50, principal))
                .assertNext(response -> assertThat(response.data().records().getFirst().amount()).isEqualTo("-0.1"))
                .verifyComplete();
        StepVerifier.create(controller.equity(21L, 1, 100, principal))
                .assertNext(response -> assertThat(response.data().records().getFirst().equity()).isEqualTo("100.9"))
                .verifyComplete();

        verify(queryService).fills(101L, 21L, 501L, FROM, TO, 0, 100);
        verify(queryService).positions(101L, 21L);
        verify(queryService).ledger(101L, 21L, 0, 50);
        verify(queryService).equity(101L, 21L, 0, 100);
    }
}
