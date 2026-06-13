package top.egon.mario.rbac.service.model;

/**
 * Event raised after permission or API rule changes so runtime caches can refresh.
 */
public record RbacPermissionChangedEvent(String reason) {
}
