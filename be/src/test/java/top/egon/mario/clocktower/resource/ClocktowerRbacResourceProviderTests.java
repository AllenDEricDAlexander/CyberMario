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
                        "menu:clocktower:rules",
                        "menu:clocktower:replays",
                        "api:clocktower:scripts:*",
                        "api:clocktower:terms:read",
                        "api:clocktower:jinx-rules:read",
                        "api:clocktower:boards:*",
                        "api:clocktower:rooms:read:list",
                        "api:clocktower:rooms:read:detail",
                        "api:clocktower:rooms:player:join",
                        "api:clocktower:rooms:player:leave",
                        "api:clocktower:rooms:player:view",
                        "api:clocktower:rooms:player:action",
                        "api:clocktower:rooms:storyteller:create",
                        "api:clocktower:rooms:storyteller:start",
                        "api:clocktower:rooms:storyteller:seat",
                        "api:clocktower:rooms:storyteller:night",
                        "api:clocktower:rooms:storyteller:flow",
                        "api:clocktower:rooms:storyteller:night-task",
                        "api:clocktower:rooms:storyteller:nomination",
                        "api:clocktower:rooms:storyteller:execution",
                        "api:clocktower:rooms:storyteller:action",
                        "api:clocktower:rooms:storyteller:ruling",
                        "api:clocktower:rooms:storyteller:ruling:detail",
                        "api:clocktower:events:stream",
                        "api:clocktower:grimoire:*",
                        "api:clocktower:replays:*")
                .doesNotContain("api:clocktower:rooms:*");
        assertThat(new HashSet<>(resourceCodes)).hasSameSizeAs(resourceCodes);
        assertThat(provider.rolePresets()).extracting(role -> role.roleCode())
                .contains("CLOCKTOWER_PLAYER", "CLOCKTOWER_STORYTELLER")
                .doesNotContain("SUPER_ADMIN");
        assertThat(provider.rolePresets())
                .filteredOn(role -> role.roleCode().equals("CLOCKTOWER_PLAYER"))
                .singleElement()
                .satisfies(role -> {
                    assertThat(role.permissionCodes()).contains(
                            "api:clocktower:scripts:*",
                            "api:clocktower:terms:read",
                            "api:clocktower:jinx-rules:read",
                            "api:clocktower:rooms:player:view");
                    assertThat(role.permissionCodes())
                            .noneMatch(code -> code.startsWith("api:clocktower:rooms:storyteller:"));
                });
        assertThat(provider.rolePresets())
                .filteredOn(role -> role.roleCode().equals("CLOCKTOWER_STORYTELLER"))
                .singleElement()
                .satisfies(role -> assertThat(role.permissionCodes()).contains(
                        "menu:clocktower:rules",
                        "menu:clocktower:replays",
                        "api:clocktower:terms:read",
                        "api:clocktower:jinx-rules:read",
                        "api:clocktower:rooms:storyteller:ruling",
                        "api:clocktower:rooms:storyteller:flow",
                        "api:clocktower:rooms:storyteller:night-task",
                        "api:clocktower:rooms:storyteller:nomination",
                        "api:clocktower:rooms:storyteller:execution",
                        "api:clocktower:rooms:storyteller:ruling:detail"));
    }
}
