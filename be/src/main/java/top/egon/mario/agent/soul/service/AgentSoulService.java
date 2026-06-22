package top.egon.mario.agent.soul.service;

import top.egon.mario.agent.soul.dto.request.AgentSoulMdUpdateRequest;
import top.egon.mario.agent.soul.dto.response.AgentSoulMdResponse;
import top.egon.mario.agent.soul.dto.response.AgentSoulMdVersionResponse;
import top.egon.mario.agent.soul.service.model.AgentSoulEvolutionRequest;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

public interface AgentSoulService {

    AgentSoulMdResponse currentSoul(RbacPrincipal principal);

    AgentSoulMdResponse updateManual(AgentSoulMdUpdateRequest request, RbacPrincipal principal);

    List<AgentSoulMdVersionResponse> versions(RbacPrincipal principal);

    String userSoulPromptForChat(RbacPrincipal principal);

    void maybeEvolveAfterChat(AgentSoulEvolutionRequest request);
}
