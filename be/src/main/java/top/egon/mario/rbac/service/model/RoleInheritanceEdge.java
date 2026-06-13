package top.egon.mario.rbac.service.model;

/**
 * Directed role inheritance edge: roleId inherits inheritedRoleId.
 */
public record RoleInheritanceEdge(Long roleId, Long inheritedRoleId) {
}
