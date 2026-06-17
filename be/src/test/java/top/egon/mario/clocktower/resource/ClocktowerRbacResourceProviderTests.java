package top.egon.mario.clocktower.resource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClocktowerRbacResourceProviderTests {

    @Test
    void providerDeclaresClocktowerMenusApisAndPresetRoles() {
        ClocktowerRbacResourceProvider provider = new ClocktowerRbacResourceProvider();

        assertThat(provider.appCode()).isEqualTo("clocktower");
        assertThat(provider.resources()).extracting(resource -> resource.code())
                .contains("menu:clocktower:boards",
                        "menu:clocktower:rooms",
                        "api:clocktower:rooms:*",
                        "api:clocktower:events:stream",
                        "api:clocktower:grimoire:*");
        assertThat(provider.rolePresets()).extracting(role -> role.roleCode())
                .contains("CLOCKTOWER_PLAYER", "CLOCKTOWER_STORYTELLER");
    }
}
