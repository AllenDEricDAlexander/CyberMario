package top.egon.mario.investment.agent.overview;

import org.springframework.stereotype.Component;
import top.egon.mario.investment.agent.po.InvestmentAgentDecisionPo;
import top.egon.mario.investment.agent.po.InvestmentAgentRunPo;
import top.egon.mario.investment.agent.repository.InvestmentAgentDecisionRepository;
import top.egon.mario.investment.agent.repository.InvestmentAgentRunRepository;
import top.egon.mario.investment.common.model.InvestmentRunStatus;
import top.egon.mario.investment.overview.InvestmentOverviewSectionContributor;
import top.egon.mario.investment.overview.dto.InvestmentOverviewSectionResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Owner-scoped recent Agent decisions at the overview's shared cutoff. */
@Component
public class AgentOverviewSectionContributor implements InvestmentOverviewSectionContributor {

    private final InvestmentAgentRunRepository runRepository;
    private final InvestmentAgentDecisionRepository decisionRepository;

    public AgentOverviewSectionContributor(
            InvestmentAgentRunRepository runRepository,
            InvestmentAgentDecisionRepository decisionRepository) {
        this.runRepository = runRepository;
        this.decisionRepository = decisionRepository;
    }

    @Override
    public String sectionCode() {
        return "AGENT";
    }

    @Override
    public int order() {
        return 500;
    }

    @Override
    public InvestmentOverviewSectionResponse contribute(OverviewContext context) {
        List<InvestmentAgentRunPo> runs = runRepository
                .findTop5ByWorkspaceIdAndStatusAndFinishedAtLessThanEqualAndDeletedFalseOrderByFinishedAtDescIdDesc(
                        context.workspaceId(), InvestmentRunStatus.SUCCEEDED, context.dataAsOf());
        Map<Long, InvestmentAgentDecisionPo> decisionsByRunId = runs.isEmpty() ? Map.of()
                : decisionRepository.findByRunIdInOrderByRunIdAscIdAsc(
                                runs.stream().map(InvestmentAgentRunPo::getId).toList())
                        .stream().collect(Collectors.toMap(
                                InvestmentAgentDecisionPo::getRunId,
                                Function.identity(),
                                (first, ignored) -> first,
                                LinkedHashMap::new));
        List<Map<String, Object>> recent = runs.stream()
                .map(run -> run(run, decisionsByRunId.get(run.getId())))
                .toList();
        return new InvestmentOverviewSectionResponse(sectionCode(), "AVAILABLE", context.dataAsOf(),
                Map.of("recentRuns", recent), null);
    }

    private Map<String, Object> run(InvestmentAgentRunPo run, InvestmentAgentDecisionPo decision) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", run.getId());
        result.put("runType", run.getRunType());
        result.put("accountId", run.getAccountId());
        result.put("reportId", run.getReportId());
        result.put("dataAsOf", run.getDataAsOf());
        result.put("finishedAt", run.getFinishedAt());
        if (decision != null) {
            result.put("decisionId", decision.getId());
            result.put("instrumentId", decision.getInstrumentId());
            result.put("action", decision.getAction());
            result.put("confidence", decision.getConfidence().toPlainString());
            result.put("executionStatus", decision.getExecutionStatus());
            result.put("intentId", decision.getIntentId());
        }
        return result;
    }
}
