package top.egon.mario.investment.research.report;

import org.springframework.stereotype.Component;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Auto-discovered Strategy registry for report generators owned by installed phases.
 */
@Component
public class InvestmentResearchReportGeneratorRegistry {

    private final Map<InvestmentReportType, InvestmentResearchReportGenerator> generators;

    public InvestmentResearchReportGeneratorRegistry(List<InvestmentResearchReportGenerator> candidates) {
        EnumMap<InvestmentReportType, InvestmentResearchReportGenerator> registered =
                new EnumMap<>(InvestmentReportType.class);
        for (InvestmentResearchReportGenerator candidate : candidates) {
            InvestmentResearchReportGenerator existing = registered.putIfAbsent(candidate.reportType(), candidate);
            if (existing != null) {
                throw new IllegalStateException("Duplicate research report generator: " + candidate.reportType());
            }
        }
        this.generators = Map.copyOf(registered);
    }

    public InvestmentResearchReportGenerator require(InvestmentReportType reportType) {
        InvestmentResearchReportGenerator generator = generators.get(reportType);
        if (generator == null) {
            throw new InvestmentException(InvestmentErrorCode.CAPABILITY_UNAVAILABLE,
                    "Research report capability is not installed: " + reportType);
        }
        return generator;
    }
}
