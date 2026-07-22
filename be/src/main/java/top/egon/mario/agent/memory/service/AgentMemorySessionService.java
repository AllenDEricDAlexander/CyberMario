package top.egon.mario.agent.memory.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import top.egon.mario.agent.memory.po.AgentMemorySessionPo;
import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemorySessionStatus;
import top.egon.mario.agent.memory.service.model.AgentMemorySessionCreate;
import top.egon.mario.agent.memory.service.model.AgentMemorySessionUpdate;
import top.egon.mario.rbac.service.security.RbacPrincipal;

public interface AgentMemorySessionService {

    AgentMemorySessionPo create(AgentMemorySessionCreate request, RbacPrincipal principal);

    Page<AgentMemorySessionPo> page(AgentMemoryEntryType entryType, AgentMemorySessionStatus status,
                                    Pageable pageable, RbacPrincipal principal);

    AgentMemorySessionPo resolveOrCreate(AgentMemoryEntryType entryType, String sessionId,
                                         Boolean memoryEnabled, Boolean longTermExtractionEnabled,
                                         RbacPrincipal principal);

    AgentMemorySessionPo resolveOrCreateExternal(Long ownerUserId, String memorySpaceId);

    AgentMemorySessionPo requireOwned(String sessionId, RbacPrincipal principal);

    AgentMemorySessionPo requireUsableForChat(String sessionId, RbacPrincipal principal);

    AgentMemorySessionPo update(String sessionId, AgentMemorySessionUpdate request, RbacPrincipal principal);

    AgentMemorySessionPo release(String sessionId, RbacPrincipal principal);

    AgentMemorySessionPo restore(String sessionId, RbacPrincipal principal);

    AgentMemorySessionPo archive(String sessionId, RbacPrincipal principal);

    void deleteArchived(String sessionId, RbacPrincipal principal);
}
