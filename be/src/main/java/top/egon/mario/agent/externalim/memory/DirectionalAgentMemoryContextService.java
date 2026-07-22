package top.egon.mario.agent.externalim.memory;

import top.egon.mario.agent.externalim.model.ChatInvocation;
import top.egon.mario.agent.memory.po.AgentMemorySessionPo;
import top.egon.mario.agent.memory.service.model.AgentMemoryContext;
import top.egon.mario.rbac.service.security.RbacPrincipal;

public interface DirectionalAgentMemoryContextService {

    AgentMemoryContext webContext(AgentMemorySessionPo webSession, RbacPrincipal principal,
                                  String selectedMemorySpaceId, boolean longTermMemoryEnabled);

    AgentMemoryContext externalContext(AgentMemorySessionPo externalSession,
                                       Long currentObservationId, boolean longTermMemoryEnabled);

    String guardGroupWindow(ChatInvocation invocation, Long currentObservationId);
}
