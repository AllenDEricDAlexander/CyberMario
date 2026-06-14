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
                .filteredOn(seed -> seed.type() == PermissionType.MENU)
                .extracting(seed -> seed.code())
                .containsExactly("menu:agent");
        assertThat(provider.resources())
                .filteredOn(seed -> "menu:agent".equals(seed.code()))
                .singleElement()
                .satisfies(seed -> assertThat(seed.menu().routePath()).isEqualTo("/dashboard"));
    }

    @Test
    void resourcesContainArxivLogApi() {
        AgentDashboardRbacResourceProvider provider = new AgentDashboardRbacResourceProvider();

        assertThat(provider.resources())
                .filteredOn(seed -> seed.type() == PermissionType.API)
                .extracting(seed -> seed.code())
                .contains("api:agent:arxiv-log:collection", "api:agent:arxiv-log:*");
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
                                    "api:agent:model-audit:dashboard:user-options",
                                    "menu:rag:arxiv-logs",
                                    "api:agent:arxiv-log:*");
                });
    }

}
