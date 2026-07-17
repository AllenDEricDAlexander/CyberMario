package top.egon.mario.im;

import org.junit.jupiter.api.Test;
import top.egon.mario.im.resource.ImRbacResourceProvider;
import top.egon.mario.rbac.po.enums.PermissionType;
import top.egon.mario.rbac.service.ApiRuleMatcher;
import top.egon.mario.rbac.service.model.ApiPermissionRule;
import top.egon.mario.rbac.service.resource.model.RbacResourceSeed;
import top.egon.mario.rbac.service.resource.model.RbacRolePresetSeed;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ImRbacResourceProviderTests {

    private final ApiRuleMatcher matcher = new ApiRuleMatcher();

    @Test
    void providerDeclaresMenuMethodSpecificApisAndManagedRoles() {
        ImRbacResourceProvider provider = new ImRbacResourceProvider();
        List<RbacResourceSeed> resources = provider.resources();

        assertThat(provider.appCode()).isEqualTo("im");
        assertThat(resources).filteredOn(seed -> seed.type() == PermissionType.MENU)
                .singleElement()
                .satisfies(seed -> {
                    assertThat(seed.code()).isEqualTo("menu:im");
                    assertThat(seed.menu().routePath()).isEqualTo("/im");
                });
        assertThat(apiCodes(resources)).containsExactlyInAnyOrder(
                "api:im:read",
                "api:im:write",
                "api:im:write:patch",
                "api:im:write:delete",
                "api:im:instance:read",
                "api:im:instance:write",
                "api:im:instance:delete",
                "api:im:platform-admin"
        );
        assertThat(resources).filteredOn(seed -> seed.type() == PermissionType.API)
                .allSatisfy(seed -> assertThat(seed.api().publicFlag()).isFalse());

        List<RbacRolePresetSeed> presets = provider.rolePresets();
        assertThat(presets).extracting(RbacRolePresetSeed::roleCode)
                .containsExactly("IM_USER", "IM_ADMIN");
        RbacRolePresetSeed user = preset(presets, "IM_USER");
        RbacRolePresetSeed admin = preset(presets, "IM_ADMIN");
        assertThat(user.permissionCodes())
                .contains("menu:im", "api:im:read", "api:im:write", "api:im:instance:write",
                        "api:rbac:auth:self", "api:rbac:me:self")
                .doesNotContain("api:im:platform-admin");
        assertThat(admin.permissionCodes())
                .containsAll(user.permissionCodes())
                .contains("api:im:platform-admin");
    }

    @Test
    void normalPlatformRoutesMapToUserPermissions() {
        List<ApiPermissionRule> rules = rules(new ImRbacResourceProvider());

        assertMatches(rules, "GET", "/api/im/platform/bootstrap", "api:im:read");
        assertMatches(rules, "GET", "/api/im/platform/conversations", "api:im:read");
        assertMatches(rules, "GET", "/api/im/platform/users", "api:im:read");
        assertMatches(rules, "GET", "/api/im/platform/friends", "api:im:read");
        assertMatches(rules, "GET", "/api/im/platform/friend-requests", "api:im:read");
        assertMatches(rules, "GET", "/api/im/platform/groups", "api:im:read");
        assertMatches(rules, "GET", "/api/im/platform/channels", "api:im:read");
        assertMatches(rules, "GET", "/api/im/platform/channels/7701/groups", "api:im:read");
        assertMatches(rules, "GET", "/api/im/conversations/7701/messages", "api:im:read");

        assertMatches(rules, "POST", "/api/im/messages", "api:im:write");
        assertMatches(rules, "POST", "/api/im/conversations/7701/read", "api:im:write");
        assertMatches(rules, "POST", "/api/im/platform/groups", "api:im:write");
        assertMatches(rules, "POST", "/api/im/platform/channels", "api:im:write");
        assertMatches(rules, "POST", "/api/im/platform/channels/7701/groups", "api:im:write");
        assertMatches(rules, "POST", "/api/im/platform/friend-requests", "api:im:write");
        assertMatches(rules, "POST", "/api/im/platform/friend-requests/9901/accept", "api:im:write");
        assertMatches(rules, "POST", "/api/im/platform/friend-requests/9901/reject", "api:im:write");
        assertMatches(rules, "POST", "/api/im/platform/friend-requests/9901/cancel", "api:im:write");
        assertMatches(rules, "PATCH", "/api/im/platform/friends/8801", "api:im:write:patch");
        assertMatches(rules, "DELETE", "/api/im/platform/friends/8801", "api:im:write:delete");
        assertMatches(rules, "POST", "/api/im/join-requests", "api:im:write");
        assertMatches(rules, "POST", "/api/im/join-requests/9901/cancel", "api:im:write");
        assertMatches(rules, "POST", "/api/im/surfaces/GROUP/3301/leave", "api:im:write");
        assertMatches(rules, "POST", "/api/im/dms", "api:im:write");
        assertMatches(rules, "POST", "/api/im/dms/block", "api:im:write");
        assertMatches(rules, "POST", "/api/im/dms/unblock", "api:im:write");
        assertMatches(rules, "POST", "/api/im/ws-ticket", "api:im:write");
    }

    @Test
    void instanceAndPlatformAdminRoutesStaySeparated() {
        List<ApiPermissionRule> rules = rules(new ImRbacResourceProvider());

        assertMatches(rules, "GET", "/api/im/surfaces/GROUP/3301/members", "api:im:instance:read");
        assertMatches(rules, "GET", "/api/im/surfaces/GROUP/3301/join-requests", "api:im:instance:read");
        assertMatches(rules, "POST", "/api/im/join-requests/9901/approve", "api:im:instance:write");
        assertMatches(rules, "POST", "/api/im/join-requests/9901/reject", "api:im:instance:write");
        assertMatches(rules, "POST", "/api/im/governance/mute", "api:im:instance:write");
        assertMatches(rules, "POST", "/api/im/governance/announcement", "api:im:instance:write");
        assertMatches(rules, "POST", "/api/im/governance/ban", "api:im:instance:write");
        assertMatches(rules, "DELETE", "/api/im/surfaces/GROUP/3301/members/8801", "api:im:instance:delete");

        assertMatches(rules, "GET", "/api/im/channels", "api:im:platform-admin");
        assertMatches(rules, "POST", "/api/im/channels", "api:im:platform-admin");
        assertMatches(rules, "GET", "/api/im/groups", "api:im:platform-admin");
        assertMatches(rules, "POST", "/api/im/groups", "api:im:platform-admin");
        assertMatches(rules, "POST", "/api/im/governance/global-mute", "api:im:platform-admin");

        assertThat(matcher.match("DELETE", "/api/im/messages/8801", rules)).isEmpty();
    }

    private List<ApiPermissionRule> rules(ImRbacResourceProvider provider) {
        return provider.resources().stream()
                .filter(resource -> resource.api() != null)
                .map(resource -> new ApiPermissionRule(resource.code(), resource.api().httpMethod(),
                        resource.api().urlPattern(), resource.api().matcherType(), resource.api().publicFlag()))
                .toList();
    }

    private List<String> apiCodes(List<RbacResourceSeed> resources) {
        return resources.stream()
                .filter(resource -> resource.type() == PermissionType.API)
                .map(RbacResourceSeed::code)
                .toList();
    }

    private RbacRolePresetSeed preset(List<RbacRolePresetSeed> presets, String roleCode) {
        return presets.stream().filter(seed -> roleCode.equals(seed.roleCode())).findFirst().orElseThrow();
    }

    private void assertMatches(List<ApiPermissionRule> rules, String method, String path, String permissionCode) {
        Optional<ApiPermissionRule> rule = matcher.match(method, path, rules);

        assertThat(rule)
                .as("%s %s", method, path)
                .map(ApiPermissionRule::permissionCode)
                .contains(permissionCode);
    }
}
