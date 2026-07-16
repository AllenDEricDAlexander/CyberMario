package top.egon.mario.investment.agent.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import top.egon.mario.investment.agent.po.InvestmentAgentDecisionPo;
import top.egon.mario.investment.agent.po.InvestmentAgentRunPo;
import top.egon.mario.investment.agent.repository.InvestmentAgentDecisionRepository;
import top.egon.mario.investment.agent.repository.InvestmentAgentRunRepository;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.research.indicator.ResearchHashSupport;
import top.egon.mario.investment.research.po.InvestmentResearchReportPo;
import top.egon.mario.investment.research.repository.InvestmentResearchReportRepository;
import top.egon.mario.investment.research.report.GeneratedResearchReport;
import top.egon.mario.investment.research.report.InvestmentReportType;
import top.egon.mario.investment.research.report.InvestmentResearchReportGenerator;
import top.egon.mario.investment.research.report.ResearchEvidenceSource;
import top.egon.mario.investment.research.report.ResearchEvidenceSourceService;
import top.egon.mario.investment.research.report.ResearchReportEvidenceDraft;
import top.egon.mario.investment.research.report.ResearchReportGenerationContext;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the immutable AGENT_ANALYSIS artifact only from a validated persisted decision. */
@Component
public class AgentAnalysisReportGenerator implements InvestmentResearchReportGenerator {

    private final InvestmentResearchReportRepository reportRepository;
    private final InvestmentAgentRunRepository runRepository;
    private final InvestmentAgentDecisionRepository decisionRepository;
    private final ResearchEvidenceSourceService evidenceSourceService;
    private final ObjectMapper objectMapper;

    public AgentAnalysisReportGenerator(
            InvestmentResearchReportRepository reportRepository,
            InvestmentAgentRunRepository runRepository,
            InvestmentAgentDecisionRepository decisionRepository,
            ResearchEvidenceSourceService evidenceSourceService,
            ObjectMapper objectMapper) {
        this.reportRepository = reportRepository;
        this.runRepository = runRepository;
        this.decisionRepository = decisionRepository;
        this.evidenceSourceService = evidenceSourceService;
        this.objectMapper = objectMapper;
    }

    @Override
    public InvestmentReportType reportType() {
        return InvestmentReportType.AGENT_ANALYSIS;
    }

    @Override
    public GeneratedResearchReport generate(ResearchReportGenerationContext context) {
        InvestmentResearchReportPo report = reportRepository.findByIdAndDeletedFalse(context.reportId())
                .orElseThrow(() -> unavailable("Agent report does not exist"));
        if (!"AGENT".equals(report.getSourceType()) || report.getSourceReferenceId() == null) {
            throw unavailable("Agent report has no validated run source");
        }
        InvestmentAgentRunPo run = runRepository.findByIdAndDeletedFalse(report.getSourceReferenceId())
                .filter(value -> value.getWorkspaceId() == context.workspaceId())
                .filter(value -> value.getDataAsOf().equals(context.input().dataAsOf()))
                .orElseThrow(() -> unavailable("Agent report run violates its owner or cutoff boundary"));
        List<InvestmentAgentDecisionPo> decisions = decisionRepository.findByRunIdOrderByIdAsc(run.getId());
        if (decisions.isEmpty()) {
            throw unavailable("Agent report run has no validated decision");
        }
        InvestmentAgentDecisionPo decision = decisions.getFirst();
        Map<String, Object> metrics = metrics(run, decision);
        String canonical = json(metrics);
        Instant cutoff = run.getDataAsOf();
        ResearchEvidenceSource source = decision.getInstrumentId() == null
                ? evidenceSourceService.requireMarketSources().getFirst()
                : evidenceSourceService.requireInstrumentSource(decision.getInstrumentId());
        ResearchReportEvidenceDraft evidence = new ResearchReportEvidenceDraft(
                "AGENT_DECISION", source.sourceId(), decision.getInstrumentId(), cutoff, cutoff, cutoff,
                "agent-run:%d/decision:%d".formatted(run.getId(), decision.getId()),
                ResearchHashSupport.sha256(canonical),
                json(Map.of("sourceCode", source.sourceCode(), "externalSymbol", source.externalSymbol())));
        String thesis = safeMarkdown(decision.getThesis());
        String summary = "%s，置信度 %s，执行状态 %s。".formatted(
                decision.getAction(), decision.getConfidence().toPlainString(), decision.getExecutionStatus());
        String markdown = """
                # Agent 模拟投资分析

                - 数据截止：%s
                - 运行类型：%s
                - 决策：%s
                - 置信度：%s
                - 时间范围：%s
                - 执行状态：%s

                ## 分析依据

                %s

                该报告仅用于模拟盘分析，不构成实盘投资建议。
                """.formatted(cutoff, run.getRunType(), decision.getAction(),
                decision.getConfidence().toPlainString(), safeMarkdown(decision.getHorizon()),
                decision.getExecutionStatus(), thesis);
        return new GeneratedResearchReport(
                "Agent 分析 · " + cutoff, summary, markdown, metrics, null, List.of(evidence));
    }

    private Map<String, Object> metrics(InvestmentAgentRunPo run, InvestmentAgentDecisionPo decision) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("agentRunId", run.getId());
        metrics.put("decisionId", decision.getId());
        metrics.put("runType", run.getRunType());
        metrics.put("instrumentId", decision.getInstrumentId());
        metrics.put("action", decision.getAction());
        metrics.put("confidence", decision.getConfidence().toPlainString());
        metrics.put("horizon", decision.getHorizon());
        metrics.put("risks", decision.getRisksJson());
        metrics.put("invalidation", decision.getInvalidationJson());
        metrics.put("executionStatus", decision.getExecutionStatus());
        metrics.put("intentId", decision.getIntentId());
        metrics.put("dataAsOf", decision.getDataAsOf());
        return metrics;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize Agent report evidence", exception);
        }
    }

    private static String safeMarkdown(String value) {
        return value == null ? "" : value.replace("<", "&lt;").replace(">", "&gt;");
    }

    private static InvestmentException unavailable(String message) {
        return new InvestmentException(InvestmentErrorCode.DATA_UNAVAILABLE, message);
    }
}
