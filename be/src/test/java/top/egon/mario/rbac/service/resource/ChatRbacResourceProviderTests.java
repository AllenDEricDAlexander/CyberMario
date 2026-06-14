package top.egon.mario.rbac.service.resource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Chat role presets include normal-user console permissions.
 */
class ChatRbacResourceProviderTests {

    @Test
    void chatBasicCanUseChatAndCurrentUserSelfService() {
        ChatRbacResourceProvider provider = new ChatRbacResourceProvider();

        assertThat(provider.rolePresets())
                .singleElement()
                .satisfies(seed -> {
                    assertThat(seed.roleCode()).isEqualTo("CHAT_BASIC");
                    assertThat(seed.permissionCodes())
                            .contains("menu:chat", "api:chat:stream",
                                    "api:rbac:auth:self", "api:rbac:me:self",
                                    "menu:agent",
                                    "api:agent:model-audit:dashboard:self")
                            .doesNotContain("api:rbac:admin:*",
                                    "api:agent:model-audit:dashboard:global",
                                    "api:agent:model-audit:dashboard:user-options");
                });
    }

}
