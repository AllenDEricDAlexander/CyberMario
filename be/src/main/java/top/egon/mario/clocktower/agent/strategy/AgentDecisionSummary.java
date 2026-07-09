package top.egon.mario.clocktower.agent.strategy;

import top.egon.mario.clocktower.agent.memory.service.ClocktowerAgentMemoryService.ClocktowerAgentMemoryRefreshResult;
import top.egon.mario.clocktower.agent.runtime.po.ClocktowerAgentTaskPo;
import top.egon.mario.clocktower.agent.view.dto.AgentLegalIntentView;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AgentDecisionSummary {

    private AgentDecisionSummary() {
    }

    public static Map<String, Object> build(ClocktowerAgentTaskPo task,
                                            AgentDecision decision,
                                            List<AgentLegalIntentView> legalIntents,
                                            List<ClocktowerGameActionResponse> responses,
                                            ClocktowerAgentMemoryRefreshResult memoryRefresh,
                                            boolean illegalIntentDowngraded) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("policy", HeuristicAgentPolicy.POLICY_NAME);
        result.put("triggerType", task.getTriggerType());
        result.put("legalIntents", legalIntents.stream().map(AgentLegalIntentView::intentType).distinct().toList());
        result.put("selectedIntent", intentType(decision.intent()));
        result.put("reasoningSummary", decision.reasoningSummary());
        result.put("diagnostics", decision.diagnostics());
        result.put("illegalIntentDowngraded", illegalIntentDowngraded);
        result.put("accepted", responses.stream().allMatch(ClocktowerGameActionResponse::accepted));
        result.put("actions", responses.stream().map(AgentDecisionSummary::actionResult).toList());
        result.put("memoryLastSeenEventSeq", memoryRefresh.lastSeenEventSeq());
        result.put("memoryInsertedCount", memoryRefresh.insertedCount());
        return result;
    }

    public static String intentType(AgentIntent intent) {
        if (intent instanceof AgentIntent.PublicSpeech) {
            return "PUBLIC_SPEECH";
        }
        if (intent instanceof AgentIntent.GrabMic) {
            return "GRAB_MIC";
        }
        if (intent instanceof AgentIntent.FinishSpeech) {
            return "FINISH_SPEECH";
        }
        if (intent instanceof AgentIntent.Nominate) {
            return "NOMINATE";
        }
        if (intent instanceof AgentIntent.Vote) {
            return "VOTE";
        }
        if (intent instanceof AgentIntent.NightChoice) {
            return "NIGHT_CHOICE";
        }
        if (intent instanceof AgentIntent.Pass) {
            return "PASS";
        }
        return "NOOP";
    }

    private static Map<String, Object> actionResult(ClocktowerGameActionResponse response) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", response.accepted());
        result.put("rejectedCode", response.rejectedCode());
        if (response.event() != null) {
            result.put("actionType", actionType(response.event().eventType()));
            result.put("eventId", response.event().eventId());
            result.put("eventType", response.event().eventType());
        }
        return result;
    }

    private static String actionType(String eventType) {
        return switch (eventType) {
            case "PLAYER_PASSED" -> "PASS";
            case "NOMINATION_OPENED" -> "NOMINATE";
            case "VOTE_CAST" -> "VOTE";
            case "NIGHT_CHOICE_SUBMITTED" -> "NIGHT_CHOICE";
            default -> eventType;
        };
    }
}
