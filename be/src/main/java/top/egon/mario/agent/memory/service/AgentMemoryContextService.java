package top.egon.mario.agent.memory.service;

import top.egon.mario.agent.memory.po.AgentMemorySessionPo;
import top.egon.mario.agent.memory.service.model.AgentMemoryContext;
import top.egon.mario.rbac.service.security.RbacPrincipal;

public interface AgentMemoryContextService {

    AgentMemoryContext contextFor(AgentMemorySessionPo session, RbacPrincipal principal);
}
