package top.egon.mario.investment.research.report;

import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deterministic generator output waiting for one short persistence transaction.
 */
public record GeneratedResearchReport(
        String title,
        String summary,
        String contentMarkdown,
        Map<String, Object> metrics,
        Object indicatorSnapshot,
        List<ResearchReportEvidenceDraft> evidence
) {

    public GeneratedResearchReport {
        metrics = Collections.unmodifiableMap(new LinkedHashMap<>(metrics));
        evidence = List.copyOf(evidence);
    }
}
