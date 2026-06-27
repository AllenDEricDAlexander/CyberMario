package top.egon.mario.im;

import org.junit.jupiter.api.Test;
import top.egon.mario.im.resource.ImRbacResourceProvider;
import top.egon.mario.rbac.po.enums.PermissionType;
import top.egon.mario.rbac.service.ApiRuleMatcher;
import top.egon.mario.rbac.service.model.ApiPermissionRule;
import top.egon.mario.rbac.service.resource.model.RbacResourceSeed;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ImRbacResourceProviderTests {

    private final ApiRuleMatcher matcher = new ApiRuleMatcher();

    @Test
    void providerDeclaresExactlyThreeCoarseImApiPermissions() {
        ImRbacResourceProvider provider = new ImRbacResourceProvider();
        List<RbacResourceSeed> resources = provider.resources();
        List<String> apiCodes = apiCodes(resources);

        assertThat(provider.appCode()).isEqualTo("im");
        assertThat(apiCodes).containsExactlyInAnyOrder("api:im:read", "api:im:write", "api:im:admin");
        assertThat(resources)
                .allSatisfy(seed -> {
                    assertThat(seed.type()).isEqualTo(PermissionType.API);
                    assertThat(seed.api()).isNotNull();
                    assertThat(seed.api().publicFlag()).isFalse();
                });
        assertThat(apiCodes)
                .noneMatch(code -> code.contains("channel"))
                .noneMatch(code -> code.contains("group"))
                .noneMatch(code -> code.contains("conversation"))
                .noneMatch(code -> code.contains("message"));
    }

    @Test
    void providerMapsReadWriteAndAdminRoutesToCoarsePermissions() {
        List<ApiPermissionRule> rules = rules(new ImRbacResourceProvider());

        assertMatches(rules, "GET", "/api/im/conversations", "api:im:read");
        assertMatches(rules, "GET", "/api/im/conversations/7701/messages", "api:im:read");
        assertMatches(rules, "GET", "/api/im/channels", "api:im:read");
        assertMatches(rules, "GET", "/api/im/groups", "api:im:read");

        assertMatches(rules, "POST", "/api/im/messages", "api:im:write");
        assertMatches(rules, "POST", "/api/im/conversations/7701/read", "api:im:write");
        assertMatches(rules, "POST", "/api/im/channels", "api:im:write");
        assertMatches(rules, "POST", "/api/im/groups", "api:im:write");
        assertMatches(rules, "POST", "/api/im/join-requests", "api:im:write");
        assertMatches(rules, "POST", "/api/im/join-requests/9901/cancel", "api:im:write");
        assertMatches(rules, "POST", "/api/im/surfaces/CHANNEL/3301/leave", "api:im:write");
        assertMatches(rules, "POST", "/api/im/dms", "api:im:write");
        assertMatches(rules, "POST", "/api/im/dms/block", "api:im:write");
        assertMatches(rules, "POST", "/api/im/dms/unblock", "api:im:write");
        assertMatches(rules, "POST", "/api/im/ws-ticket", "api:im:write");

        assertMatches(rules, "POST", "/api/im/join-requests/9901/approve", "api:im:admin");
        assertMatches(rules, "POST", "/api/im/join-requests/9901/reject", "api:im:admin");
        assertMatches(rules, "POST", "/api/im/governance/mute", "api:im:admin");
        assertMatches(rules, "POST", "/api/im/governance/global-mute", "api:im:admin");
        assertMatches(rules, "POST", "/api/im/governance/announcement", "api:im:admin");
        assertMatches(rules, "POST", "/api/im/governance/ban", "api:im:admin");

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

    private void assertMatches(List<ApiPermissionRule> rules, String method, String path, String permissionCode) {
        Optional<ApiPermissionRule> rule = matcher.match(method, path, rules);

        assertThat(rule)
                .as("%s %s", method, path)
                .map(ApiPermissionRule::permissionCode)
                .contains(permissionCode);
    }
}
