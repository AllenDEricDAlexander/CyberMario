package top.egon.mario.agent.model.service.resource;

import org.springframework.stereotype.Component;
import top.egon.mario.rbac.po.enums.PermissionStatus;
import top.egon.mario.rbac.service.resource.RbacResourceProvider;
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
        return List.of(menu());
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

    private RbacResourceSeed menu() {
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

}
