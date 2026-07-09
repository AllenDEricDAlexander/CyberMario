package top.egon.mario.clocktower.agent.view.service;

import top.egon.mario.clocktower.agent.view.dto.AgentPrivateView;

public interface ClocktowerAgentPrivateViewService {

    AgentPrivateView build(Long gameId, Long agentInstanceId);
}
