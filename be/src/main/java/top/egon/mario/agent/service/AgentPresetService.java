package top.egon.mario.agent.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import top.egon.mario.agent.dto.request.AgentDebugChatRequest;
import top.egon.mario.agent.dto.request.AgentPresetRequest;
import top.egon.mario.agent.dto.request.AgentPresetStatusRequest;
import top.egon.mario.agent.dto.response.AgentPresetResponse;
import top.egon.mario.agent.service.model.AgentRuntimeSpec;
import top.egon.mario.rbac.service.security.RbacPrincipal;

/**
 * Manages saved agent debug presets and resolves runtime configuration.
 */
public interface AgentPresetService {

    Page<AgentPresetResponse> page(Pageable pageable);

    AgentPresetResponse detail(Long id);

    AgentPresetResponse create(AgentPresetRequest request, RbacPrincipal principal);

    AgentPresetResponse update(Long id, AgentPresetRequest request, RbacPrincipal principal);

    AgentPresetResponse updateStatus(Long id, AgentPresetStatusRequest request, RbacPrincipal principal);

    void delete(Long id, RbacPrincipal principal);

    AgentRuntimeSpec resolveRuntimeSpec(AgentDebugChatRequest request);

    AgentRuntimeSpec defaultRuntimeSpec();

    AgentRuntimeSpec externalImRuntimeSpec();

    String serializeRuntimeSpec(AgentRuntimeSpec spec);

}
