package top.egon.mario.clocktower.resource;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClocktowerRbacResourceProviderTests {

    @Test
    void providerDeclaresClocktowerMenusApisAndPresetRoles() {
        ClocktowerRbacResourceProvider provider = new ClocktowerRbacResourceProvider();

        assertThat(provider.appCode()).isEqualTo("clocktower");
        List<String> resourceCodes = provider.resources().stream()
                .map(resource -> resource.code())
                .toList();
        assertThat(resourceCodes)
                .contains("menu:clocktower:boards",
                        "menu:clocktower:rooms",
                        "api:clocktower:rooms:read:list",
                        "api:clocktower:rooms:read:detail",
                        "api:clocktower:rooms:player:view",
                        "api:clocktower:rooms:storyteller:start",
                        "api:clocktower:events:stream",
                        "api:clocktower:grimoire:*")
                .doesNotContain("api:clocktower:rooms:*");
        assertThat(new HashSet<>(resourceCodes)).hasSameSizeAs(resourceCodes);
        assertThat(provider.rolePresets()).extracting(role -> role.roleCode())
                .contains("CLOCKTOWER_PLAYER", "CLOCKTOWER_STORYTELLER");
        assertThat(provider.rolePresets())
                .filteredOn(role -> role.roleCode().equals("CLOCKTOWER_PLAYER"))
                .singleElement()
                .satisfies(role -> {
                    assertThat(role.permissionCodes()).contains("api:clocktower:rooms:player:view");
                    assertThat(role.permissionCodes())
                            .noneMatch(code -> code.startsWith("api:clocktower:rooms:storyteller:"));
                });
    }
}
