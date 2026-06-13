package top.egon.mario.rbac.service;

import org.junit.jupiter.api.Test;
import top.egon.mario.rbac.service.model.RoleInheritanceEdge;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleHierarchyResolverTests {

    private final RoleHierarchyResolver resolver = new RoleHierarchyResolver();

    @Test
    void resolveEffectiveRoleIdsExpandsInheritedRolesTransitively() {
        Set<Long> effectiveRoleIds = resolver.resolveEffectiveRoleIds(
                Set.of(1L),
                List.of(
                        new RoleInheritanceEdge(1L, 2L),
                        new RoleInheritanceEdge(2L, 3L),
                        new RoleInheritanceEdge(4L, 5L)
                )
        );

        assertThat(effectiveRoleIds).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void assertNoCycleRejectsIndirectRoleInheritanceCycle() {
        assertThatThrownBy(() -> resolver.assertNoCycle(
                3L,
                Set.of(1L),
                List.of(
                        new RoleInheritanceEdge(1L, 2L),
                        new RoleInheritanceEdge(2L, 3L)
                )
        )).isInstanceOf(RbacException.class)
                .hasMessageContaining("RBAC_ROLE_INHERIT_CYCLE");
    }

}
