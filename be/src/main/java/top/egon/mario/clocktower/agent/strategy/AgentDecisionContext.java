package top.egon.mario.clocktower.agent.strategy;

import top.egon.mario.clocktower.agent.po.ClocktowerAgentProfilePo;
import top.egon.mario.clocktower.agent.view.dto.AgentLegalIntentView;
import top.egon.mario.clocktower.agent.view.dto.AgentPrivateView;

import java.util.List;
import java.util.Map;

public record AgentDecisionContext(
        AgentPrivateView view,
        ClocktowerAgentProfilePo profile,
        List<AgentLegalIntentView> legalIntents,
        String triggerType,
        Map<String, Object> taskMetadata,
        Map<String, Object> runtimeState
) {
}
