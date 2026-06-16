package top.egon.mario.agent.memory.service;

import top.egon.mario.agent.memory.po.AgentMemoryMessagePo;
import top.egon.mario.agent.memory.po.AgentMemorySessionPo;
import top.egon.mario.agent.memory.service.model.AgentMemoryMessageRecord;
import top.egon.mario.agent.memory.service.model.AgentMemoryTurn;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

public interface AgentMemoryMessageService {

    List<AgentMemoryMessagePo> appendAll(List<AgentMemoryMessageRecord> records);

    List<AgentMemoryMessagePo> messages(String sessionId, RbacPrincipal principal);

    List<AgentMemoryTurn> recentTurns(AgentMemorySessionPo session);

    int nextTurnNo(String sessionId);
}
