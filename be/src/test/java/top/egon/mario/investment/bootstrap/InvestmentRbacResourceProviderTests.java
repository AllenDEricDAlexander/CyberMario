package top.egon.mario.investment.bootstrap;

import org.junit.jupiter.api.Test;
import top.egon.mario.rbac.po.enums.PermissionType;
import top.egon.mario.rbac.service.ApiRuleMatcher;
import top.egon.mario.rbac.service.model.ApiPermissionRule;
import top.egon.mario.rbac.service.resource.model.RbacResourceSeed;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the Investment RBAC resource and role contracts shared with the web client.
 */
class InvestmentRbacResourceProviderTests {

    @Test
    void declaresMenuApiAndButtonPermissionCodes() {
        InvestmentRbacResourceProvider provider = new InvestmentRbacResourceProvider();
        List<RbacResourceSeed> resources = provider.resources();

        assertThat(provider.appCode()).isEqualTo("investment");
        assertThat(resources).extracting(RbacResourceSeed::code)
                .contains(
                        "menu:investment",
                        "menu:investment:workspace",
                        "menu:investment:platform",
                        "api:investment:market:*",
                        "api:investment:workspace:*",
                        "api:investment:private-detail:*",
                        "api:investment:platform:*",
                        "btn:investment:workspace:create",
                        "btn:investment:watchlist:manage",
                        "btn:investment:paper:trade",
                        "btn:investment:agent:run",
                        "btn:investment:platform:retry-job",
                        "btn:investment:platform:pull-market-data");
        assertThat(resources)
                .filteredOn(seed -> seed.type() == PermissionType.MENU)
                .extracting(RbacResourceSeed::code)
                .containsExactly("menu:investment", "menu:investment:workspace", "menu:investment:platform");

        assertThat(resources)
                .filteredOn(seed -> "btn:investment:watchlist:manage".equals(seed.code()))
                .singleElement()
                .satisfies(seed -> {
                    assertThat(seed.type()).isEqualTo(PermissionType.BUTTON);
                    assertThat(seed.parentCode()).isEqualTo("menu:investment:workspace");
                    assertThat(seed.buttonApiCodes()).containsExactly("api:investment:private-detail:*");
                });
    }

    @Test
    void keepsPlatformAdministrationSeparateFromPrivateWorkspacePermissions() {
        InvestmentRbacResourceProvider provider = new InvestmentRbacResourceProvider();

        assertThat(provider.rolePresets()).extracting("roleCode")
                .containsExactlyInAnyOrder("INVESTMENT_USER", "INVESTMENT_PLATFORM_ADMIN");
        assertThat(provider.rolePresets())
                .filteredOn(role -> "INVESTMENT_USER".equals(role.roleCode()))
                .singleElement()
                .satisfies(role -> assertThat(role.permissionCodes())
                        .contains("api:investment:workspace:*", "api:investment:private-detail:*")
                        .doesNotContain("menu:investment:platform", "api:investment:platform:*"));
        assertThat(provider.rolePresets())
                .filteredOn(role -> "INVESTMENT_PLATFORM_ADMIN".equals(role.roleCode()))
                .singleElement()
                .satisfies(role -> assertThat(role.permissionCodes())
                        .contains("menu:investment:platform", "api:investment:platform:*",
                                "btn:investment:platform:pull-market-data")
                        .doesNotContain("menu:investment:workspace", "api:investment:workspace:*",
                                "api:investment:private-detail:*"));
    }

    @Test
    void apiRulesMatchCollectionAndOwnerScopedChildRoutes() {
        List<ApiPermissionRule> rules = new InvestmentRbacResourceProvider().resources().stream()
                .filter(seed -> seed.api() != null)
                .map(seed -> new ApiPermissionRule(
                        seed.code(), seed.api().httpMethod(), seed.api().urlPattern(),
                        seed.api().matcherType(), seed.api().publicFlag()))
                .toList();
        ApiRuleMatcher matcher = new ApiRuleMatcher();

        assertThat(matcher.match("GET", "/api/investment/workspaces", rules))
                .get().extracting(ApiPermissionRule::permissionCode)
                .isEqualTo("api:investment:workspace:*");
        assertThat(matcher.match("POST", "/api/investment/watchlists/21/items", rules))
                .get().extracting(ApiPermissionRule::permissionCode)
                .isEqualTo("api:investment:private-detail:*");
        assertThat(matcher.match("POST", "/api/investment/platform/jobs/9/retry", rules))
                .get().extracting(ApiPermissionRule::permissionCode)
                .isEqualTo("api:investment:platform:*");
    }
}
