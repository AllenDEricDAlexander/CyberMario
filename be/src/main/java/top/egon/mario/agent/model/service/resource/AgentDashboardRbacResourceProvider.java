package top.egon.mario.agent.model.service.resource;

import org.springframework.stereotype.Component;
import top.egon.mario.rbac.po.enums.ApiMatcherType;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;
import top.egon.mario.rbac.po.enums.PermissionStatus;
import top.egon.mario.rbac.service.resource.RbacResourceProvider;
import top.egon.mario.rbac.service.resource.model.RbacApiSeed;
import top.egon.mario.rbac.service.resource.model.RbacMenuSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSource;
import top.egon.mario.rbac.service.resource.model.RbacRolePresetSeed;

import java.util.List;

/**
 * Supplies the AI dashboard menu to the RBAC resource synchronizer.
 */
@Component
public class AgentDashboardRbacResourceProvider implements RbacResourceProvider {

    private static final String APP_CODE = "agent";

    @Override
    public String appCode() {
        return APP_CODE;
    }

    @Override
    public List<RbacResourceSeed> resources() {
        return List.of(dashboardMenu(), dashboardSelfApi(), dashboardGlobalApi(), arxivLogCollectionApi(), arxivLogApi());
    }

    @Override
    public List<RbacRolePresetSeed> rolePresets() {
        return List.of(new RbacRolePresetSeed(
                APP_CODE,
                "AGENT_DASHBOARD_USER",
                "Agent Dashboard User",
                "System role for AI model usage dashboards.",
                45,
                List.of("menu:agent",
                        "api:agent:model-audit:dashboard:self"),
                RbacResourceSource.PROVIDER
        ));
    }

    private RbacResourceSeed dashboardMenu() {
        return RbacResourceSeed.menu(
                APP_CODE,
                APP_CODE,
                "menu:agent",
                "首页控制台",
                null,
                PermissionStatus.ENABLED,
                10,
                "AI model usage dashboard",
                new RbacMenuSeed("dashboard", "/dashboard", null, null, "DashboardOutlined", false, true, null),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed dashboardSelfApi() {
        return RbacResourceSeed.api(
                APP_CODE,
                APP_CODE,
                "api:agent:model-audit:dashboard:self",
                "AI 用量个人控制台",
                PermissionStatus.ENABLED,
                0,
                "Personal AI model usage dashboard APIs",
                new RbacApiSeed("GET", "/api/agent/model-audit/dashboard/self/**", ApiMatcherType.ANT, false, ApiRiskLevel.MEDIUM),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed dashboardGlobalApi() {
        return RbacResourceSeed.api(
                APP_CODE,
                APP_CODE,
                "api:agent:model-audit:dashboard:global",
                "AI 用量全局控制台",
                PermissionStatus.ENABLED,
                0,
                "Global AI model usage dashboard APIs",
                new RbacApiSeed("GET", "/api/agent/model-audit/dashboard/global/**", ApiMatcherType.ANT, false, ApiRiskLevel.HIGH),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed arxivLogApi() {
        return RbacResourceSeed.api(
                APP_CODE,
                APP_CODE,
                "api:agent:arxiv-log:*",
                "arXiv 工具日志管理",
                PermissionStatus.ENABLED,
                0,
                "Super-admin-only arXiv tool logs",
                new RbacApiSeed("ANY", "/api/admin/agent/arxiv/logs/**", ApiMatcherType.ANT, false, ApiRiskLevel.HIGH),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed arxivLogCollectionApi() {
        return RbacResourceSeed.api(
                APP_CODE,
                APP_CODE,
                "api:agent:arxiv-log:collection",
                "arXiv 工具日志集合",
                PermissionStatus.ENABLED,
                0,
                "Super-admin-only arXiv tool log collection",
                new RbacApiSeed("GET", "/api/admin/agent/arxiv/logs", ApiMatcherType.EXACT, false, ApiRiskLevel.HIGH),
                RbacResourceSource.PROVIDER
        );
    }

}
