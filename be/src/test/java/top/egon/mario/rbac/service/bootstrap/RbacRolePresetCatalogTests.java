package top.egon.mario.rbac.service.bootstrap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies static role preset permission catalogs stay aligned with feature role expansions.
 */
class RbacRolePresetCatalogTests {

    @Test
    void chatUserIncludesAgentDebugButNotConversationAudit() {
        RbacRolePresetCatalog catalog = new RbacRolePresetCatalog();

        assertThat(catalog.roles())
                .filteredOn(role -> "CHAT_USER".equals(role.roleCode()))
                .singleElement()
                .satisfies(role -> assertThat(role.permissionCodes())
                        .contains("menu:agent:debug",
                                "api:agent:debug:chat:stream",
                                "api:agent:preset:collection",
                                "api:agent:preset:*")
                        .doesNotContain("menu:agent:conversation-audit",
                                "api:agent:conversation-audit:collection",
                                "api:agent:conversation-audit:*",
                                "menu:agent:run-audit",
                                "api:agent:run-audit:collection",
                                "api:agent:run-audit:*"));
    }

}
