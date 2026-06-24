package top.egon.mario.clocktower.resource;

import org.junit.jupiter.api.Test;
import top.egon.mario.rbac.service.ApiRuleMatcher;
import top.egon.mario.rbac.service.model.ApiPermissionRule;
import top.egon.mario.rbac.service.resource.RbacResourceProvider;
import top.egon.mario.rbac.service.resource.model.RbacResourceSeed;
import top.egon.mario.rbac.service.resource.model.RbacRolePresetSeed;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ClocktowerRbacResourceProviderTests {

    private final ApiRuleMatcher matcher = new ApiRuleMatcher();

    private static final Path RETIRE_OLD_RBAC_MIGRATION = Path.of(
            "src/main/resources/db/migration/V27__retire_old_clocktower_rbac_resources.sql");

    private static final List<String> RETIRED_PERMISSION_CODES = List.of(
            "api:clocktower:rooms:*",
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
            "api:clocktower:rooms:chat:*",
            "api:clocktower:chat:*",
            "api:clocktower:rooms:storyteller:create",
            "api:clocktower:rooms:storyteller:start",
            "api:clocktower:rooms:storyteller:seat",
            "api:clocktower:rooms:storyteller:game:start",
            "api:clocktower:games:storyteller:end",
            "api:clocktower:games:storyteller:abort",
            "api:clocktower:rooms:storyteller:game:timeout-abort",
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
            "api:clocktower:replays:*"
    );

    @Test
    void providerDeclaresClocktowerMenusApisAndPresetRoles() {
        List<RbacResourceProvider> providers = providers();
        ClocktowerRbacResourceProvider provider = new ClocktowerRbacResourceProvider();

        assertThat(provider.appCode()).isEqualTo("clocktower");
        assertThat(new ClocktowerRbacResourceProvider.AdminProvider().appCode()).isEqualTo("admin");
        List<RbacResourceSeed> resources = providers.stream()
                .map(RbacResourceProvider::resources)
                .flatMap(Collection::stream)
                .toList();
        List<String> resourceCodes = resources.stream()
                .map(resource -> resource.code())
                .toList();
        assertThat(resourceCodes)
                .contains("menu:clocktower:boards",
                        "menu:clocktower:rooms",
                        "menu:clocktower:rules",
                        "menu:clocktower:replays",
                        "menu:clocktower:admin-audit");
        assertThat(apiCodes(resources))
                .containsExactlyInAnyOrder(
                        "api:clocktower:script:read",
                        "api:clocktower:board:*",
                        "api:clocktower:room:read",
                        "api:clocktower:room:create",
                        "api:clocktower:room:membership",
                        "api:clocktower:room:seat",
                        "api:clocktower:room:governance",
                        "api:clocktower:game:read",
                        "api:clocktower:game:lifecycle",
                        "api:clocktower:game:action",
                        "api:clocktower:game:storyteller",
                        "api:clocktower:game:event-stream",
                        "api:clocktower:game:replay",
                        "api:clocktower:chat:read",
                        "api:clocktower:chat:send",
                        "api:clocktower:chat:conversation",
                        "api:clocktower:chat:read-state",
                        "api:admin:clocktower:audit",
                        "api:admin:clocktower:rule-data");
        assertThat(resourceCodes)
                .doesNotContain("api:clocktower:rooms:*",
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
                        "api:clocktower:rooms:chat:*",
                        "api:clocktower:chat:*",
                        "api:clocktower:rooms:storyteller:create",
                        "api:clocktower:rooms:storyteller:start",
                        "api:clocktower:rooms:storyteller:seat",
                        "api:clocktower:rooms:storyteller:game:start",
                        "api:clocktower:games:storyteller:end",
                        "api:clocktower:games:storyteller:abort",
                        "api:clocktower:rooms:storyteller:game:timeout-abort",
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
                .noneMatch(code -> code.startsWith("api:im:"));
        assertThat(resources.stream()
                .map(RbacResourceSeed::api)
                .filter(api -> api != null)
                .map(api -> api.urlPattern())
                .toList())
                .noneMatch(pattern -> pattern.contains("/api/im"));
        assertThat(new HashSet<>(resourceCodes)).hasSameSizeAs(resourceCodes);
        List<RbacRolePresetSeed> rolePresets = providers.stream()
                .map(RbacResourceProvider::rolePresets)
                .flatMap(Collection::stream)
                .toList();
        assertThat(rolePresets).extracting(role -> role.roleCode())
                .contains("CLOCKTOWER_PLAYER", "CLOCKTOWER_STORYTELLER", "CLOCKTOWER_ADMIN")
                .doesNotContain("CLOCKTOWER_SPECTATOR", "SUPER_ADMIN");
        assertThat(rolePresets)
                .filteredOn(role -> role.roleCode().equals("CLOCKTOWER_PLAYER"))
                .singleElement()
                .satisfies(role -> {
                    assertThat(role.permissionCodes()).contains(
                            "api:clocktower:script:read",
                            "api:clocktower:room:read",
                            "api:clocktower:room:membership",
                            "api:clocktower:room:seat",
                            "api:clocktower:game:read",
                            "api:clocktower:game:action",
                            "api:clocktower:game:event-stream",
                            "api:clocktower:game:replay",
                            "api:clocktower:chat:read",
                            "api:clocktower:chat:send",
                            "api:clocktower:chat:conversation",
                            "api:clocktower:chat:read-state");
                    assertThat(role.permissionCodes())
                            .noneMatch(code -> code.startsWith("api:admin:"));
                    assertThat(role.permissionCodes())
                            .doesNotContain("api:clocktower:board:*",
                                    "api:clocktower:room:create",
                                    "api:clocktower:room:governance",
                                    "api:clocktower:game:lifecycle",
                                    "api:clocktower:game:storyteller");
                });
        assertThat(rolePresets)
                .filteredOn(role -> role.roleCode().equals("CLOCKTOWER_STORYTELLER"))
                .singleElement()
                .satisfies(role -> assertThat(role.permissionCodes()).contains(
                        "menu:clocktower:rules",
                        "menu:clocktower:replays",
                        "api:clocktower:script:read",
                        "api:clocktower:board:*",
                        "api:clocktower:room:create",
                        "api:clocktower:room:governance",
                        "api:clocktower:game:lifecycle",
                        "api:clocktower:game:storyteller",
                        "api:clocktower:game:replay"));
        assertThat(rolePresets)
                .filteredOn(role -> role.roleCode().equals("CLOCKTOWER_ADMIN"))
                .singleElement()
                .satisfies(role -> assertThat(role.permissionCodes()).containsExactlyInAnyOrder(
                        "menu:clocktower:admin-audit",
                        "api:admin:clocktower:audit",
                        "api:admin:clocktower:rule-data"));
    }

    @Test
    void providerMapsCurrentClocktowerApisToCanonicalResources() {
        List<ApiPermissionRule> rules = providers().stream()
                .map(RbacResourceProvider::resources)
                .flatMap(Collection::stream)
                .filter(resource -> resource.api() != null)
                .map(resource -> new ApiPermissionRule(resource.code(), resource.api().httpMethod(),
                        resource.api().urlPattern(), resource.api().matcherType(), resource.api().publicFlag()))
                .toList();

        assertMatches(rules, "GET", "/api/clocktower/scripts/trouble-brewing/roles",
                "api:clocktower:script:read");
        assertMatches(rules, "GET", "/api/clocktower/terms", "api:clocktower:script:read");
        assertMatches(rules, "GET", "/api/clocktower/jinx-rules", "api:clocktower:script:read");
        assertMatches(rules, "POST", "/api/clocktower/boards/generate", "api:clocktower:board:*");
        assertMatches(rules, "GET", "/api/clocktower/boards", "api:clocktower:board:*");
        assertMatches(rules, "GET", "/api/clocktower/rooms", "api:clocktower:room:read");
        assertMatches(rules, "GET", "/api/clocktower/rooms/7", "api:clocktower:room:read");
        assertMatches(rules, "POST", "/api/clocktower/rooms", "api:clocktower:room:create");
        assertMatches(rules, "POST", "/api/clocktower/rooms/7/enter", "api:clocktower:room:membership");
        assertMatches(rules, "POST", "/api/clocktower/rooms/7/heartbeat", "api:clocktower:room:membership");
        assertMatches(rules, "POST", "/api/clocktower/rooms/7/invitations/3/accept",
                "api:clocktower:room:membership");
        assertMatches(rules, "POST", "/api/clocktower/rooms/7/seats/2/claim", "api:clocktower:room:seat");
        assertMatches(rules, "PATCH", "/api/clocktower/rooms/7/seats/2", "api:clocktower:room:seat");
        assertMatches(rules, "PATCH", "/api/clocktower/rooms/7/board", "api:clocktower:room:governance");
        assertMatches(rules, "POST", "/api/clocktower/rooms/7/invitations", "api:clocktower:room:governance");
        assertMatches(rules, "POST", "/api/clocktower/rooms/7/members/4/kick",
                "api:clocktower:room:governance");
        assertMatches(rules, "GET", "/api/clocktower/games/9/view", "api:clocktower:game:read");
        assertMatches(rules, "POST", "/api/clocktower/rooms/7/games/start",
                "api:clocktower:game:lifecycle");
        assertMatches(rules, "POST", "/api/clocktower/games/9/end", "api:clocktower:game:lifecycle");
        assertMatches(rules, "POST", "/api/clocktower/games/9/actions", "api:clocktower:game:action");
        assertMatches(rules, "GET", "/api/clocktower/rooms/7/grimoire", "api:clocktower:game:storyteller");
        assertMatches(rules, "POST", "/api/clocktower/rooms/7/flow/advance",
                "api:clocktower:game:storyteller");
        assertMatches(rules, "GET", "/api/clocktower/games/9/events/stream",
                "api:clocktower:game:event-stream");
        assertMatches(rules, "GET", "/api/clocktower/games/9/replay", "api:clocktower:game:replay");
        assertMatches(rules, "GET", "/api/clocktower/games/history", "api:clocktower:game:replay");
        assertMatches(rules, "GET", "/api/clocktower/rooms/7/chat/conversations",
                "api:clocktower:chat:conversation");
        assertMatches(rules, "POST", "/api/clocktower/chat/conversations",
                "api:clocktower:chat:conversation");
        assertMatches(rules, "GET", "/api/clocktower/chat/conversations/5/messages",
                "api:clocktower:chat:read");
        assertMatches(rules, "POST", "/api/clocktower/chat/conversations/5/messages",
                "api:clocktower:chat:send");
        assertMatches(rules, "POST", "/api/clocktower/chat/conversations/5/read",
                "api:clocktower:chat:read-state");
        assertMatches(rules, "GET", "/api/admin/clocktower/games/9/audit",
                "api:admin:clocktower:audit");
        assertMatches(rules, "POST", "/api/admin/clocktower/rule-data/reindex",
                "api:admin:clocktower:rule-data");
        assertThat(matcher.match("GET", "/api/im/conversations", rules)).isEmpty();
    }

    @Test
    void migrationRetiresOldClocktowerRbacResourcesWithoutDisablingCanonicalCodes() throws IOException {
        assertThat(Files.exists(RETIRE_OLD_RBAC_MIGRATION)).isTrue();

        String sql = Files.readString(RETIRE_OLD_RBAC_MIGRATION);

        assertThat(sql).contains(RETIRED_PERMISSION_CODES.toArray(String[]::new));
        assertThat(sql).contains("UPDATE sys_role");
        assertThat(sql).contains("permission_version = permission_version + 1");
        assertThat(sql).contains("DELETE");
        assertThat(sql).contains("FROM sys_role_permission");
        assertThat(sql).contains("UPDATE sys_permission");
        assertThat(sql).contains("status = 0");
        assertThat(sql).contains("version = version + 1");
        assertThat(sql).doesNotContain(
                "api:clocktower:script:read",
                "api:clocktower:board:*",
                "api:clocktower:room:read",
                "api:clocktower:game:read",
                "api:clocktower:game:replay",
                "api:clocktower:chat:read",
                "api:admin:clocktower:audit",
                "api:admin:clocktower:rule-data");
    }

    private List<RbacResourceProvider> providers() {
        return List.of(new ClocktowerRbacResourceProvider(), new ClocktowerRbacResourceProvider.AdminProvider());
    }

    private List<String> apiCodes(List<RbacResourceSeed> resources) {
        return resources.stream()
                .filter(resource -> resource.api() != null)
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
