package top.egon.mario.investment.overview;

import org.junit.jupiter.api.Test;
import top.egon.mario.investment.common.access.InvestmentAccessService;
import top.egon.mario.investment.overview.dto.InvestmentOverviewResponse;
import top.egon.mario.investment.overview.dto.InvestmentOverviewSectionResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Enforces the aggregator-owned temporal boundary independently of domain implementations. */
class InvestmentOverviewCutoffTests {

    private static final Instant CUTOFF = Instant.parse("2040-01-01T00:00:00Z");

    @Test
    void passesExactlyOneServerCutoffToEveryContributorInStableSectionOrder() {
        List<InvestmentOverviewSectionContributor.OverviewContext> received = new ArrayList<>();
        InvestmentOverviewQueryService service = new InvestmentOverviewQueryService(
                mock(InvestmentAccessService.class),
                List.of(
                        contributor("AGENT", 500, received),
                        contributor("PORTFOLIO", 400, received),
                        contributor("QUANT", 300, received),
                        contributor("MARKET", 100, received)),
                Clock.fixed(CUTOFF, ZoneOffset.UTC));

        InvestmentOverviewResponse response = service.overview(101L, 11L);

        assertThat(received).hasSize(4).allSatisfy(context -> {
            assertThat(context.actorId()).isEqualTo(101L);
            assertThat(context.workspaceId()).isEqualTo(11L);
            assertThat(context.dataAsOf()).isEqualTo(CUTOFF);
        });
        assertThat(response.sections()).extracting(InvestmentOverviewSectionResponse::code)
                .containsExactly("MARKET", "QUANT", "PORTFOLIO", "AGENT");
        assertThat(response.sections()).extracting(InvestmentOverviewSectionResponse::dataAsOf)
                .containsOnly(CUTOFF);
    }

    @Test
    void rejectsAContributorBoundaryLaterThanTheSharedCutoffAndPreservesOtherSections() {
        InvestmentOverviewSectionContributor later = new InvestmentOverviewSectionContributor() {
            @Override
            public String sectionCode() {
                return "QUANT";
            }

            @Override
            public int order() {
                return 300;
            }

            @Override
            public InvestmentOverviewSectionResponse contribute(OverviewContext context) {
                return new InvestmentOverviewSectionResponse(
                        sectionCode(), "AVAILABLE", context.dataAsOf().plusSeconds(1), Map.of("count", 1), null);
            }
        };
        InvestmentOverviewQueryService service = new InvestmentOverviewQueryService(
                mock(InvestmentAccessService.class),
                List.of(contributor("MARKET", 100, new ArrayList<>()), later),
                Clock.fixed(CUTOFF, ZoneOffset.UTC));

        InvestmentOverviewResponse response = service.overview(101L, 11L);

        assertThat(response.sections()).extracting(
                        InvestmentOverviewSectionResponse::code,
                        InvestmentOverviewSectionResponse::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("MARKET", "AVAILABLE"),
                        org.assertj.core.groups.Tuple.tuple("QUANT", "ERROR"),
                        org.assertj.core.groups.Tuple.tuple("PORTFOLIO", "UNAVAILABLE"),
                        org.assertj.core.groups.Tuple.tuple("AGENT", "UNAVAILABLE"));
        assertThat(response.sections()).extracting(InvestmentOverviewSectionResponse::dataAsOf)
                .containsOnly(CUTOFF);
    }

    private InvestmentOverviewSectionContributor contributor(
            String code, int order,
            List<InvestmentOverviewSectionContributor.OverviewContext> received) {
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
                received.add(context);
                return new InvestmentOverviewSectionResponse(
                        code, "AVAILABLE", context.dataAsOf(), Map.of("count", 1), null);
            }
        };
    }
}
