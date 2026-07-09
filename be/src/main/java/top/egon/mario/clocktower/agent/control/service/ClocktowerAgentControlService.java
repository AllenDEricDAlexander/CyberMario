package top.egon.mario.clocktower.agent.control.service;

import top.egon.mario.clocktower.agent.control.dto.ClocktowerAgentConsoleView;
import top.egon.mario.clocktower.agent.control.dto.ClocktowerAgentMemoryView;
import top.egon.mario.clocktower.agent.control.dto.ClocktowerAgentTaskView;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

public interface ClocktowerAgentControlService {

    List<ClocktowerAgentConsoleView> listAgents(Long gameId, RbacPrincipal principal);

    ClocktowerAgentConsoleView pauseAgent(Long gameId, Long agentInstanceId, RbacPrincipal principal);

    ClocktowerAgentConsoleView resumeAgent(Long gameId, Long agentInstanceId, RbacPrincipal principal);

    ClocktowerAgentTaskView runNow(Long gameId, Long agentInstanceId, RbacPrincipal principal);

    List<ClocktowerAgentMemoryView> listMemory(Long gameId, Long agentInstanceId, RbacPrincipal principal);

    List<ClocktowerAgentTaskView> listTasks(Long gameId, Long agentInstanceId, RbacPrincipal principal);
}
