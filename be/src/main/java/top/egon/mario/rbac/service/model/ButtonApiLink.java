package top.egon.mario.rbac.service.model;

/**
 * Link between a button permission and an API permission.
 */
public record ButtonApiLink(Long buttonPermissionId, Long apiPermissionId) {
}
