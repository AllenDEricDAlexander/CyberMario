package top.egon.mario.rbac.service;

import org.junit.jupiter.api.Test;
import top.egon.mario.rbac.service.model.ButtonApiLink;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RolePermissionMergeServiceTests {

    private final RolePermissionMergeService mergeService = new RolePermissionMergeService();

    @Test
    void mergeButtonApisAddsMappedApisWhenSyncEnabled() {
        Set<Long> mergedPermissionIds = mergeService.mergeButtonApis(
                Set.of(100L, 200L),
                true,
                List.of(
                        new ButtonApiLink(100L, 300L),
                        new ButtonApiLink(100L, 301L),
                        new ButtonApiLink(101L, 302L)
                )
        );

        assertThat(mergedPermissionIds).containsExactlyInAnyOrder(100L, 200L, 300L, 301L);
    }

    @Test
    void mergeButtonApisKeepsSubmittedPermissionsWhenSyncDisabled() {
        Set<Long> mergedPermissionIds = mergeService.mergeButtonApis(
                Set.of(100L, 200L),
                false,
                List.of(new ButtonApiLink(100L, 300L))
        );

        assertThat(mergedPermissionIds).containsExactlyInAnyOrder(100L, 200L);
    }

}
