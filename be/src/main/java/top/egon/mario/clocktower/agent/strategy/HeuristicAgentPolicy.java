package top.egon.mario.clocktower.agent.strategy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.agent.runtime.ClocktowerAgentTriggerType;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class HeuristicAgentPolicy implements ClocktowerAgentPolicy {

    public static final String POLICY_NAME = "HEURISTIC_V0";

    private final AgentSpeechPlanner speechPlanner;
    private final AgentNominationPlanner nominationPlanner;
    private final AgentVotePlanner votePlanner;
    private final AgentNightChoicePlanner nightChoicePlanner;

    @Override
    public AgentDecision decide(AgentDecisionContext context) {
        return switch (context.triggerType()) {
            case ClocktowerAgentTriggerType.MIC_TURN_STARTED -> speechPlanner.planMicTurn(context);
            case ClocktowerAgentTriggerType.MIC_GRAB_OPENED,
                    ClocktowerAgentTriggerType.PUBLIC_EVENT_APPENDED -> speechPlanner.planGrabMic(context);
            case ClocktowerAgentTriggerType.PHASE_CHANGED -> nominationPlanner.plan(context);
            case ClocktowerAgentTriggerType.VOTE_WINDOW_OPENED -> votePlanner.plan(context);
            case ClocktowerAgentTriggerType.NIGHT_TASK_OPENED -> nightChoicePlanner.plan(context);
            default -> new AgentDecision(new AgentIntent.Noop("trigger has no heuristic action"),
                    "no heuristic action for trigger", Map.of("triggerType", context.triggerType()));
        };
    }
}
