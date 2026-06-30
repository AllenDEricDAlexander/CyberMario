package top.egon.mario.nutrition;

import org.junit.jupiter.api.Test;
import top.egon.mario.nutrition.service.bootstrap.NutritionRbacResourceProvider;
import top.egon.mario.rbac.po.enums.PermissionType;
import top.egon.mario.rbac.service.resource.model.RbacResourceSeed;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the nutrition module declares its RBAC resources through provider-owned seeds.
 */
class NutritionRbacResourceProviderTests {

    @Test
    void providerDeclaresNutritionAppAndCoreResources() {
        NutritionRbacResourceProvider provider = new NutritionRbacResourceProvider();
        List<RbacResourceSeed> resources = provider.resources();

        assertThat(provider.appCode()).isEqualTo("nutrition");
        assertThat(resources)
                .extracting(RbacResourceSeed::code)
                .contains(
                        "menu:nutrition",
                        "menu:nutrition:families",
                        "menu:nutrition:platform",
                        "api:nutrition:clan:*",
                        "api:nutrition:family:*",
                        "api:nutrition:platform:*"
                );
    }

    @Test
    void providerDeclaresMenusAndApisWithExpectedShape() {
        NutritionRbacResourceProvider provider = new NutritionRbacResourceProvider();

        assertThat(provider.resources())
                .filteredOn(seed -> "menu:nutrition:families".equals(seed.code()))
                .singleElement()
                .satisfies(seed -> {
                    assertThat(seed.type()).isEqualTo(PermissionType.MENU);
                    assertThat(seed.parentCode()).isEqualTo("menu:nutrition");
                    assertThat(seed.menu().routePath()).isEqualTo("/nutrition/families");
                });
        assertThat(provider.resources())
                .filteredOn(seed -> "api:nutrition:family:*".equals(seed.code()))
                .singleElement()
                .satisfies(seed -> {
                    assertThat(seed.type()).isEqualTo(PermissionType.API);
                    assertThat(seed.api().publicFlag()).isFalse();
                    assertThat(seed.api().urlPattern()).isEqualTo("/api/nutrition/families/**");
                });
    }

    @Test
    void rolePresetsIncludeUserAndPlatformAdminPermissions() {
        NutritionRbacResourceProvider provider = new NutritionRbacResourceProvider();

        assertThat(provider.rolePresets())
                .extracting("roleCode")
                .contains("NUTRITION_USER", "NUTRITION_PLATFORM_ADMIN");
        assertThat(provider.rolePresets())
                .filteredOn(seed -> "NUTRITION_USER".equals(seed.roleCode()))
                .singleElement()
                .satisfies(seed -> assertThat(seed.permissionCodes())
                        .contains("menu:nutrition", "menu:nutrition:families",
                                "api:nutrition:clan:*", "api:nutrition:family:*")
                        .doesNotContain("menu:nutrition:platform", "api:nutrition:platform:*"));
        assertThat(provider.rolePresets())
                .filteredOn(seed -> "NUTRITION_PLATFORM_ADMIN".equals(seed.roleCode()))
                .singleElement()
                .satisfies(seed -> assertThat(seed.permissionCodes())
                        .contains("menu:nutrition", "menu:nutrition:families",
                                "menu:nutrition:platform", "api:nutrition:clan:*",
                                "api:nutrition:family:*", "api:nutrition:platform:*"));
    }
}
