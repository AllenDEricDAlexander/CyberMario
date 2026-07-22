package top.egon.mario.agent.externalim.memory;

import top.egon.mario.agent.externalim.memory.model.ExternalChatBindingCommand;
import top.egon.mario.agent.externalim.memory.po.AgentMemorySpacePo;
import top.egon.mario.agent.externalim.memory.po.ExternalChatBindingPo;
import top.egon.mario.rbac.service.security.RbacPrincipal;

public interface AgentMemorySpaceService {

    AgentMemorySpacePo create(String name, RbacPrincipal principal);

    AgentMemorySpacePo requireOwned(String memorySpaceId, RbacPrincipal principal);

    ExternalChatBindingPo bind(ExternalChatBindingCommand command, RbacPrincipal principal);
}
