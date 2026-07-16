package top.egon.mario.investment.research;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.research.indicator.InvestmentIndicatorPoint;
import top.egon.mario.investment.research.indicator.InvestmentIndicatorService;
import top.egon.mario.investment.research.indicator.InvestmentIndicatorSnapshot;
import top.egon.mario.investment.research.report.InvestmentReportService;
import top.egon.mario.investment.research.web.InvestmentResearchController;
import top.egon.mario.investment.research.web.dto.CreateInvestmentReportRequest;
import top.egon.mario.investment.research.web.dto.CreateInvestmentReportResponse;
import top.egon.mario.investment.research.web.dto.InvestmentReportDetailResponse;
import top.egon.mario.investment.research.web.dto.InvestmentReportSummaryResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Locks the indicator/report HTTP paths, envelope and owner-scoped delegation.
 */
@ExtendWith(MockitoExtension.class)
class InvestmentResearchControllerTests {

    private static final Instant NOW = Instant.parse("2030-02-01T00:00:00Z");

    @Mock
    private InvestmentIndicatorService indicatorService;
    @Mock
    private InvestmentReportService reportService;

    private InvestmentResearchController controller;
    private RbacPrincipal principal;

    @BeforeEach
    void setUp() {
        controller = new InvestmentResearchController(indicatorService, reportService);
        ReflectionTestUtils.setField(controller, "blockingScheduler", Schedulers.immediate());
        principal = new RbacPrincipal(101L, "owner", Set.of(), Set.of(), "v1");
    }

    @Test
    void returnsIndicatorDecimalStringsWithoutExposingTa4jTypes() {
        InvestmentIndicatorPoint point = new InvestmentIndicatorPoint(
                NOW.minusSeconds(60), "123.450000000000000001", "120.1", "121.2", "55.5",
                "1.2", "1.1", "0.1", "130", "120", "110", "3.5");
        InvestmentIndicatorSnapshot snapshot = new InvestmentIndicatorSnapshot(
                42L, PriceType.MARK, BarInterval.M1, NOW.minusSeconds(60), NOW, NOW,
                "a".repeat(64), List.of(3L), List.of(point));
        when(indicatorService.calculate(42L, PriceType.MARK, BarInterval.M1,
                NOW.minusSeconds(60), NOW, NOW)).thenReturn(snapshot);

        StepVerifier.create(controller.indicators(42L, PriceType.MARK, BarInterval.M1,
                        NOW.minusSeconds(60), NOW, NOW))
                .assertNext(response -> {
                    assertThat(response.code()).isEqualTo("0");
                    assertThat(response.data().points().getFirst().close())
                            .isEqualTo("123.450000000000000001");
                    assertThat(response.data().getClass().getName()).doesNotContain("ta4j");
                })
                .verifyComplete();
    }

    @Test
    void createsQueuedReportAndListsThroughTheWorkspaceOwner() {
        CreateInvestmentReportRequest request = new CreateInvestmentReportRequest(
                "MARKET_OVERVIEW", null, null, null, null, null);
        InvestmentReportSummaryResponse summary = summary("PENDING");
        when(reportService.create(101L, 11L, request))
                .thenReturn(new CreateInvestmentReportResponse(summary, 88L));
        when(reportService.list(eq(101L), eq(11L), eq("MARKET_OVERVIEW"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summary)));

        StepVerifier.create(controller.createReport(11L, request, principal))
                .assertNext(response -> {
                    assertThat(response.data().jobId()).isEqualTo(88L);
                    assertThat(response.data().report().status()).isEqualTo("PENDING");
                })
                .verifyComplete();
        StepVerifier.create(controller.reports(11L, "MARKET_OVERVIEW", 1, 20, principal))
                .assertNext(response -> assertThat(response.data().records()).containsExactly(summary))
                .verifyComplete();

        verify(reportService).create(101L, 11L, request);
        verify(reportService).list(eq(101L), eq(11L), eq("MARKET_OVERVIEW"), any(Pageable.class));
    }

    @Test
    void returnsDetailWithItsEvidenceInOneOwnerScopedCall() {
        InvestmentReportDetailResponse detail = new InvestmentReportDetailResponse(
                summary("READY"), "USER", "# Safe", "{}", List.of());
        when(reportService.detail(101L, 31L)).thenReturn(detail);

        StepVerifier.create(controller.report(31L, principal))
                .assertNext(response -> assertThat(response.data()).isSameAs(detail))
                .verifyComplete();
        verify(reportService).detail(101L, 31L);
    }

    @Test
    void exposesTheFrozenIndicatorAndReportRoutes() throws Exception {
        Method indicators = InvestmentResearchController.class.getMethod("indicators", long.class,
                PriceType.class, BarInterval.class, Instant.class, Instant.class, Instant.class);
        Method create = InvestmentResearchController.class.getMethod("createReport", Long.class,
                CreateInvestmentReportRequest.class, RbacPrincipal.class);
        Method detail = InvestmentResearchController.class.getMethod("report", Long.class, RbacPrincipal.class);

        assertThat(indicators.getAnnotation(GetMapping.class).value())
                .containsExactly("/market/instruments/{instrumentId}/indicators");
        assertThat(create.getAnnotation(PostMapping.class).value())
                .containsExactly("/workspaces/{workspaceId}/reports");
        assertThat(detail.getAnnotation(GetMapping.class).value())
                .containsExactly("/reports/{reportId}");
    }

    private InvestmentReportSummaryResponse summary(String status) {
        return new InvestmentReportSummaryResponse(
                31L, 11L, null, "MARKET_OVERVIEW", "Market", "Summary", status, 1L, NOW, NOW);
    }
}
