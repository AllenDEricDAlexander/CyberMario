package top.egon.mario.rbac.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rbac.service.model.RoleInheritanceEdge;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves RBAC1 role inheritance and protects role graphs from cycles.
 */
@Component
@Slf4j
public class RoleHierarchyResolver {

    public Set<Long> resolveEffectiveRoleIds(Set<Long> directRoleIds, Collection<RoleInheritanceEdge> edges) {
        Map<Long, Set<Long>> inheritedRoleIdsByRoleId = groupEdges(edges);
        Set<Long> effectiveRoleIds = new HashSet<>();
        ArrayDeque<Long> stack = new ArrayDeque<>(directRoleIds);

        while (!stack.isEmpty()) {
            Long roleId = stack.pop();
            if (!effectiveRoleIds.add(roleId)) {
                continue;
            }
            inheritedRoleIdsByRoleId.getOrDefault(roleId, Set.of()).forEach(stack::push);
        }

        return effectiveRoleIds;
    }

    public void assertNoCycle(Long roleId, Set<Long> inheritedRoleIds, Collection<RoleInheritanceEdge> existingEdges) {
        if (roleId == null || inheritedRoleIds == null || inheritedRoleIds.isEmpty()) {
            return;
        }
        if (inheritedRoleIds.contains(roleId)) {
            LogUtil.warn(log).log("rbac role inheritance rejected, reason=self_cycle, roleId={}", roleId);
            throw new RbacException("RBAC_ROLE_INHERIT_CYCLE", "role cannot inherit itself");
        }

        Map<Long, Set<Long>> inheritedRoleIdsByRoleId = groupEdges(existingEdges);
        inheritedRoleIdsByRoleId.put(roleId, new HashSet<>(inheritedRoleIds));
        for (Long inheritedRoleId : inheritedRoleIds) {
            if (reachesRole(inheritedRoleId, roleId, inheritedRoleIdsByRoleId, new HashSet<>())) {
                LogUtil.warn(log).log("rbac role inheritance rejected, reason=cycle_detected, roleId={}, inheritedRoleId={}",
                        roleId, inheritedRoleId);
                throw new RbacException("RBAC_ROLE_INHERIT_CYCLE", "role inheritance cycle detected");
            }
        }
    }

    private boolean reachesRole(Long currentRoleId, Long targetRoleId, Map<Long, Set<Long>> edges, Set<Long> visited) {
        if (Objects.equals(currentRoleId, targetRoleId)) {
            return true;
        }
        if (!visited.add(currentRoleId)) {
            return false;
        }
        for (Long inheritedRoleId : edges.getOrDefault(currentRoleId, Set.of())) {
            if (reachesRole(inheritedRoleId, targetRoleId, edges, visited)) {
                return true;
            }
        }
        return false;
    }

    private Map<Long, Set<Long>> groupEdges(Collection<RoleInheritanceEdge> edges) {
        Map<Long, Set<Long>> inheritedRoleIdsByRoleId = new HashMap<>();
        if (edges == null) {
            return inheritedRoleIdsByRoleId;
        }
        for (RoleInheritanceEdge edge : edges) {
            if (edge.roleId() == null || edge.inheritedRoleId() == null) {
                continue;
            }
            inheritedRoleIdsByRoleId
                    .computeIfAbsent(edge.roleId(), key -> new HashSet<>())
                    .add(edge.inheritedRoleId());
        }
        return inheritedRoleIdsByRoleId;
    }

}
