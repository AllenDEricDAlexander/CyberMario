package top.egon.mario.agent.memory;

import org.junit.jupiter.api.Test;
import top.egon.mario.agent.service.resource.AgentMemoryRbacResourceProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMemoryRbacResourceProviderTests {

    @Test
    void chatBasicGetsAgentMemoryPageAndSelfMemoryApis() {
        AgentMemoryRbacResourceProvider provider = new AgentMemoryRbacResourceProvider();

        List<String> permissions = provider.rolePresets().stream()
                .filter(role -> "CHAT_BASIC".equals(role.roleCode()))
                .flatMap(role -> role.permissionCodes().stream())
                .toList();

        assertThat(permissions).contains(
                "menu:agent:memory",
                "menu:agent:memory-archive",
                "api:agent:memory:session:collection",
                "api:agent:memory:session:*",
                "api:agent:memory:long-term:read",
                "api:agent:memory:extraction:read",
                "btn:agent:memory:archive",
                "btn:agent:memory:delete"
        );
    }

    @Test
    void ragUserGetsOnlySessionMemoryWithoutAgentMemoryMenusOrLongTermAudit() {
        AgentMemoryRbacResourceProvider provider = new AgentMemoryRbacResourceProvider();

        assertThat(provider.rolePresets())
                .filteredOn(role -> "RAG_USER".equals(role.roleCode()))
                .singleElement()
                .satisfies(role -> assertThat(role.permissionCodes())
                        .contains("api:agent:memory:session:collection",
                                "api:agent:memory:session:*",
                                "api:agent:memory:message:read",
                                "btn:agent:memory:switch")
                        .doesNotContain("menu:agent:memory",
                                "menu:agent:memory-archive",
                                "api:agent:memory:long-term:read",
                                "api:agent:memory:extraction:read"));
    }

    @Test
    void resourcesExposeMenusButtonsAndApiMatchers() {
        AgentMemoryRbacResourceProvider provider = new AgentMemoryRbacResourceProvider();

        assertThat(provider.resources())
                .extracting("code")
                .contains("menu:agent:memory",
                        "menu:agent:memory-archive",
                        "btn:agent:memory:switch",
                        "btn:agent:memory:release",
                        "btn:agent:memory:archive",
                        "btn:agent:memory:restore",
                        "btn:agent:memory:delete",
                        "api:agent:memory:session:collection",
                        "api:agent:memory:session:*",
                        "api:agent:memory:message:read",
                        "api:agent:memory:long-term:read",
                        "api:agent:memory:long-term:version",
                        "api:agent:memory:extraction:read");
    }
}
