package top.egon.mario.investment.research.report;

/**
 * Strategy for one code-owned research-report type.
 */
public interface InvestmentResearchReportGenerator {

    InvestmentReportType reportType();

    GeneratedResearchReport generate(ResearchReportGenerationContext context);
}
