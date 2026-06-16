package top.egon.mario.agent.model.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import top.egon.mario.agent.model.dto.request.ModelAuditDashboardQuery;
import top.egon.mario.agent.model.dto.response.ModelAuditDashboardSummaryResponse;
import top.egon.mario.agent.model.dto.response.ModelAuditRecentCallResponse;
import top.egon.mario.agent.model.dto.response.ModelAuditUserOptionResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

/**
 * Read-only service for model audit dashboard statistics.
 */
public interface ModelAuditDashboardService {

    ModelAuditDashboardSummaryResponse selfSummary(ModelAuditDashboardQuery query, RbacPrincipal principal);

    ModelAuditDashboardSummaryResponse globalSummary(ModelAuditDashboardQuery query, RbacPrincipal principal);

    Page<ModelAuditRecentCallResponse> selfRecentCalls(ModelAuditDashboardQuery query, Pageable pageable,
                                                       RbacPrincipal principal);

    Page<ModelAuditRecentCallResponse> globalRecentCalls(ModelAuditDashboardQuery query, Pageable pageable,
                                                         RbacPrincipal principal);

    List<ModelAuditUserOptionResponse> userOptions(String keyword, int size, RbacPrincipal principal);

}
