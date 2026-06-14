package top.egon.mario.agent.model.service;

import top.egon.mario.agent.model.dto.request.ModelAuditDashboardQuery;
import top.egon.mario.agent.model.dto.response.ModelAuditDashboardResponse;
import top.egon.mario.agent.model.dto.response.ModelAuditUserOptionResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

/**
 * Read-only service for model audit dashboard statistics.
 */
public interface ModelAuditDashboardService {

    ModelAuditDashboardResponse self(ModelAuditDashboardQuery query, RbacPrincipal principal);

    ModelAuditDashboardResponse global(ModelAuditDashboardQuery query, RbacPrincipal principal);

    List<ModelAuditUserOptionResponse> userOptions(String keyword, int size, RbacPrincipal principal);

}
