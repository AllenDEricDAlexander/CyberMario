package top.egon.mario.agent.memory.service;

import top.egon.mario.agent.memory.po.AgentMemoryExtractionAuditPo;
import top.egon.mario.agent.memory.service.model.AgentMemoryExtractionRequest;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

public interface AgentMemoryExtractionService {

    void extractAfterTurn(AgentMemoryExtractionRequest request);

    List<AgentMemoryExtractionAuditPo> userAudits(RbacPrincipal principal);
}
