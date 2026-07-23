package top.egon.mario.rbac.service.resource;

import org.junit.jupiter.api.Test;
import top.egon.mario.rbac.po.enums.PermissionType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies RBAC administration console resources are declared for synchronization.
 */
class RbacAdminConsoleResourceProviderTests {

    @Test
    void resourcesContainConsoleMenusAndButtonsWithoutApiRules() {
        RbacAdminConsoleResourceProvider provider = new RbacAdminConsoleResourceProvider();

        assertThat(provider.resources())
                .extracting("code")
                .contains("menu:system", "menu:system:users", "menu:system:roles",
                        "btn:system:user:add", "btn:system:role:permission", "btn:system:api:delete");
        assertThat(provider.resources())
                .filteredOn(seed -> "btn:system:user:resendActivation".equals(seed.code()))
                .singleElement()
                .satisfies(seed -> assertThat(seed.button().frontendAction()).isEqualTo("resendActivation"));
        assertThat(provider.resources())
                .noneMatch(seed -> seed.type() == PermissionType.API);
        assertThat(provider.resources().stream()
                .filter(seed -> seed.type() == PermissionType.BUTTON)
                .flatMap(seed -> seed.buttonApiCodes().stream()))
                .isEmpty();
    }

}
