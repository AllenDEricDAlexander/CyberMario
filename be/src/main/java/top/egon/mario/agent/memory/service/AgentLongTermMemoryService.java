package top.egon.mario.agent.memory.service;

import top.egon.mario.agent.memory.po.AgentLongTermMemoryPo;
import top.egon.mario.agent.memory.po.AgentLongTermMemoryVersionPo;
import top.egon.mario.agent.memory.po.enums.AgentLongTermMemoryScopeType;
import top.egon.mario.agent.memory.service.model.AgentLongTermMemoryMergeRequest;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

public interface AgentLongTermMemoryService {

    AgentLongTermMemoryPo getOrCreateUserAgentMemory(RbacPrincipal principal);

    AgentLongTermMemoryPo getOrCreate(Long userId, String username, AgentLongTermMemoryScopeType scopeType);

    AgentLongTermMemoryPo merge(AgentLongTermMemoryMergeRequest request);

    List<AgentLongTermMemoryVersionPo> versions(Long userId, AgentLongTermMemoryScopeType scopeType);
}
