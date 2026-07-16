package top.egon.mario.investment.overview;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.access.InvestmentAccessService;
import top.egon.mario.investment.overview.dto.InvestmentOverviewResponse;
import top.egon.mario.investment.overview.dto.InvestmentOverviewSectionResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies private overview ownership and the one-cutoff contributor contract.
 */
class InvestmentOverviewControllerTests {

    private static final Instant CUTOFF = Instant.parse("2030-01-01T00:00:00Z");

    @Test
    void selectsOneServerCutoffAndKeepsUnavailableSectionsInStableOrder() {
        InvestmentAccessService accessService = mock(InvestmentAccessService.class);
        AtomicReference<InvestmentOverviewSectionContributor.OverviewContext> received = new AtomicReference<>();
        InvestmentOverviewSectionContributor market = contributor("MARKET", 100, received);
        InvestmentOverviewQueryService service = new InvestmentOverviewQueryService(
                accessService, List.of(market), Clock.fixed(CUTOFF, ZoneOffset.UTC));

        InvestmentOverviewResponse response = service.overview(101L, 11L);

        verify(accessService).requireWorkspaceOwner(11L, 101L);
        assertThat(received.get().actorId()).isEqualTo(101L);
        assertThat(received.get().workspaceId()).isEqualTo(11L);
        assertThat(received.get().dataAsOf()).isEqualTo(CUTOFF);
        assertThat(response.dataAsOf()).isEqualTo(CUTOFF);
        assertThat(response.sections()).extracting(InvestmentOverviewSectionResponse::code)
                .containsExactly("MARKET", "QUANT", "PORTFOLIO", "AGENT");
        assertThat(response.sections()).extracting(InvestmentOverviewSectionResponse::status)
                .containsExactly("AVAILABLE", "UNAVAILABLE", "UNAVAILABLE", "UNAVAILABLE");
        assertThat(response.sections()).extracting(InvestmentOverviewSectionResponse::dataAsOf)
                .containsOnly(CUTOFF);
    }

    @Test
    void platformRoleDoesNotBypassPrivateWorkspaceOwnership() {
        InvestmentAccessService accessService = mock(InvestmentAccessService.class);
        doThrow(new InvestmentException(InvestmentErrorCode.FORBIDDEN, "denied"))
                .when(accessService).requireWorkspaceOwner(11L, 900L);
        InvestmentOverviewQueryService service = new InvestmentOverviewQueryService(
                accessService, List.of(), Clock.fixed(CUTOFF, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.overview(900L, 11L))
                .isInstanceOf(InvestmentException.class)
                .satisfies(exception -> assertThat(((InvestmentException) exception).getErrorCode())
                        .isEqualTo(InvestmentErrorCode.FORBIDDEN));
    }

    @Test
    void controllerBindsAuthenticatedActorRatherThanAcceptingAnActorParameter() {
        InvestmentOverviewQueryService service = mock(InvestmentOverviewQueryService.class);
        InvestmentOverviewResponse overview = new InvestmentOverviewResponse(11L, CUTOFF, List.of());
        when(service.overview(101L, 11L)).thenReturn(overview);
        InvestmentOverviewController controller = new InvestmentOverviewController(service);
        ReflectionTestUtils.setField(controller, "blockingScheduler", Schedulers.immediate());
        RbacPrincipal principal = new RbacPrincipal(
                101L, "owner", Set.of("INVESTMENT_USER"), Set.of(), "v1");

        StepVerifier.create(controller.overview(11L, principal))
                .assertNext(response -> assertThat(response.data()).isEqualTo(overview))
                .verifyComplete();

        verify(service).overview(101L, 11L);
    }

    @Test
    void rejectsDuplicateContributorStrategiesAtStartup() {
        InvestmentAccessService accessService = mock(InvestmentAccessService.class);
        var first = contributor("MARKET", 100, new AtomicReference<>());
        var duplicate = contributor("MARKET", 200, new AtomicReference<>());

        assertThatThrownBy(() -> new InvestmentOverviewQueryService(
                accessService, List.of(first, duplicate), Clock.fixed(CUTOFF, ZoneOffset.UTC)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");
    }

    private InvestmentOverviewSectionContributor contributor(
            String code, int order,
            AtomicReference<InvestmentOverviewSectionContributor.OverviewContext> received) {
        return new InvestmentOverviewSectionContributor() {
            @Override
            public String sectionCode() {
                return code;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public InvestmentOverviewSectionResponse contribute(OverviewContext context) {
                received.set(context);
                return new InvestmentOverviewSectionResponse(
                        code, "AVAILABLE", context.dataAsOf(), Map.of("count", 1L), null);
            }
        };
    }
}
