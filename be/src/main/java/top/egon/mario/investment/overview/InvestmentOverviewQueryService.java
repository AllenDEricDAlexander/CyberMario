package top.egon.mario.investment.overview;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.egon.mario.investment.common.access.InvestmentAccessService;
import top.egon.mario.investment.overview.dto.InvestmentOverviewResponse;
import top.egon.mario.investment.overview.dto.InvestmentOverviewSectionResponse;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Owner-scoped overview aggregator using ordered contributor strategies at one cutoff.
 */
@Service
public class InvestmentOverviewQueryService {

    private static final List<String> SECTION_ORDER = List.of("MARKET", "QUANT", "PORTFOLIO", "AGENT");

    private final InvestmentAccessService accessService;
    private final List<InvestmentOverviewSectionContributor> contributors;
    private final Clock clock;

    @Autowired
    public InvestmentOverviewQueryService(InvestmentAccessService accessService,
                                          List<InvestmentOverviewSectionContributor> contributors) {
        this(accessService, contributors, Clock.systemUTC());
    }

    public InvestmentOverviewQueryService(InvestmentAccessService accessService,
                                          List<InvestmentOverviewSectionContributor> contributors,
                                          Clock clock) {
        this.accessService = Objects.requireNonNull(accessService, "accessService");
        this.contributors = contributors.stream()
                .sorted(Comparator.comparingInt(InvestmentOverviewSectionContributor::order)
                        .thenComparing(InvestmentOverviewSectionContributor::sectionCode))
                .toList();
        this.clock = Objects.requireNonNull(clock, "clock");
        validateContributors(this.contributors);
    }

    public InvestmentOverviewResponse overview(Long actorId, Long workspaceId) {
        accessService.requireWorkspaceOwner(workspaceId, actorId);
        Instant cutoff = clock.instant();
        InvestmentOverviewSectionContributor.OverviewContext context =
                new InvestmentOverviewSectionContributor.OverviewContext(actorId, workspaceId, cutoff);
        Map<String, InvestmentOverviewSectionContributor> byCode = new HashMap<>();
        contributors.forEach(contributor -> byCode.put(contributor.sectionCode(), contributor));

        List<InvestmentOverviewSectionResponse> sections = new ArrayList<>(SECTION_ORDER.size());
        for (String code : SECTION_ORDER) {
            InvestmentOverviewSectionContributor contributor = byCode.get(code);
            if (contributor == null) {
                sections.add(InvestmentOverviewSectionResponse.unavailable(code, cutoff));
                continue;
            }
            sections.add(safeContribution(code, cutoff, context, contributor));
        }
        return new InvestmentOverviewResponse(workspaceId, cutoff, sections);
    }

    private InvestmentOverviewSectionResponse safeContribution(
            String code, Instant cutoff,
            InvestmentOverviewSectionContributor.OverviewContext context,
            InvestmentOverviewSectionContributor contributor) {
        try {
            InvestmentOverviewSectionResponse response = Objects.requireNonNull(
                    contributor.contribute(context), "Overview contributor response");
            if (!code.equals(response.code()) || !cutoff.equals(response.dataAsOf())) {
                return InvestmentOverviewSectionResponse.error(code, cutoff);
            }
            return response;
        } catch (RuntimeException exception) {
            return InvestmentOverviewSectionResponse.error(code, cutoff);
        }
    }

    private void validateContributors(List<InvestmentOverviewSectionContributor> candidates) {
        Map<String, InvestmentOverviewSectionContributor> seen = new HashMap<>();
        for (InvestmentOverviewSectionContributor contributor : candidates) {
            String code = Objects.requireNonNull(contributor.sectionCode(), "Overview section code");
            if (!SECTION_ORDER.contains(code)) {
                throw new IllegalStateException("Unknown Investment overview section: " + code);
            }
            if (seen.putIfAbsent(code, contributor) != null) {
                throw new IllegalStateException("Duplicate Investment overview section: " + code);
            }
        }
    }
}
