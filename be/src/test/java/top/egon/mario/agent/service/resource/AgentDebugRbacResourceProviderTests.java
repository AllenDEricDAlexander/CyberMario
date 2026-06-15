package top.egon.mario.agent.service.resource;

import org.junit.jupiter.api.Test;
import top.egon.mario.rbac.po.enums.PermissionType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies agent debug and conversation audit resources are declared for RBAC synchronization.
 */
class AgentDebugRbacResourceProviderTests {

    @Test
    void resourcesContainDebugAndAuditMenus() {
        AgentDebugRbacResourceProvider provider = new AgentDebugRbacResourceProvider();

        assertThat(provider.resources())
                .filteredOn(seed -> seed.type() == PermissionType.MENU)
                .extracting(seed -> seed.code())
                .containsExactlyInAnyOrder("menu:agent:debug", "menu:agent:conversation-audit",
                        "menu:agent:run-audit");
        assertThat(provider.resources())
                .filteredOn(seed -> "menu:agent:debug".equals(seed.code()))
                .singleElement()
                .satisfies(seed -> assertThat(seed.menu().routePath()).isEqualTo("/agent/debug"));
        assertThat(provider.resources())
                .filteredOn(seed -> "menu:agent:conversation-audit".equals(seed.code()))
                .singleElement()
                .satisfies(seed -> assertThat(seed.menu().routePath()).isEqualTo("/agent/conversation-audits"));
        assertThat(provider.resources())
                .filteredOn(seed -> "menu:agent:run-audit".equals(seed.code()))
                .singleElement()
                .satisfies(seed -> assertThat(seed.menu().routePath()).isEqualTo("/agent/run-audits"));
    }

    @Test
    void resourcesContainDebugPresetAndAuditApis() {
        AgentDebugRbacResourceProvider provider = new AgentDebugRbacResourceProvider();

        assertThat(provider.resources())
                .filteredOn(seed -> seed.type() == PermissionType.API)
                .extracting(seed -> seed.code())
                .contains("api:agent:debug:chat:stream",
                        "api:agent:preset:collection",
                        "api:agent:preset:*",
                        "api:agent:conversation-audit:collection",
                        "api:agent:conversation-audit:*",
                        "api:agent:run-audit:collection",
                        "api:agent:run-audit:*");
    }

    @Test
    void rolePresetExtendsChatBasicWithoutAuditPermission() {
        AgentDebugRbacResourceProvider provider = new AgentDebugRbacResourceProvider();

        assertThat(provider.rolePresets())
                .singleElement()
                .satisfies(seed -> {
                    assertThat(seed.roleCode()).isEqualTo("CHAT_BASIC");
                    assertThat(seed.permissionCodes())
                            .contains("menu:agent:debug",
                                    "api:agent:debug:chat:stream",
                                    "api:agent:preset:collection",
                                    "api:agent:preset:*")
                            .doesNotContain("menu:agent:conversation-audit",
                                    "api:agent:conversation-audit:collection",
                                    "api:agent:conversation-audit:*",
                                    "menu:agent:run-audit",
                                    "api:agent:run-audit:collection",
                                    "api:agent:run-audit:*");
                });
    }

}
