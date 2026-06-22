package top.egon.mario.agent.context.service;

import top.egon.mario.agent.context.service.model.AgentContext;
import top.egon.mario.agent.memory.service.model.AgentMemoryContext;
import top.egon.mario.rbac.service.security.RbacPrincipal;

public interface AgentContextAssemblyService {

    default AgentContext assemble(RbacPrincipal principal, AgentMemoryContext memoryContext) {
        return assemble(principal, memoryContext, true);
    }

    AgentContext assemble(RbacPrincipal principal, AgentMemoryContext memoryContext, boolean soulContextEnabled);
}
