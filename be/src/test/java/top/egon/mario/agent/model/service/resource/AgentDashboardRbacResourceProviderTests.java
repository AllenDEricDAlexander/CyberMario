package top.egon.mario.agent.model.service.resource;

import org.junit.jupiter.api.Test;
import top.egon.mario.rbac.po.enums.PermissionType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the AI dashboard resources and role presets are declared for RBAC synchronization.
 */
class AgentDashboardRbacResourceProviderTests {

    @Test
    void resourcesContainDashboardMenu() {
        AgentDashboardRbacResourceProvider provider = new AgentDashboardRbacResourceProvider();

        assertThat(provider.resources())
                .singleElement()
                .satisfies(seed -> {
                    assertThat(seed.code()).isEqualTo("menu:agent");
                    assertThat(seed.type()).isEqualTo(PermissionType.MENU);
                    assertThat(seed.menu().routePath()).isEqualTo("/dashboard");
                    assertThat(seed.menu().icon()).isEqualTo("DashboardOutlined");
                });
    }

    @Test
    void rolePresetsContainDashboardBusinessUser() {
        AgentDashboardRbacResourceProvider provider = new AgentDashboardRbacResourceProvider();

        assertThat(provider.rolePresets())
                .singleElement()
                .satisfies(seed -> {
                    assertThat(seed.roleCode()).isEqualTo("AGENT_DASHBOARD_USER");
                    assertThat(seed.permissionCodes())
                            .contains("menu:agent",
                                    "api:agent:model-audit:dashboard:self")
                            .doesNotContain("api:rbac:admin:*",
                                    "api:agent:model-audit:dashboard:global",
                                    "api:agent:model-audit:dashboard:user-options");
                });
    }

}
