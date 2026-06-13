package top.egon.mario.rbac.service.model;

/**
 * Runtime API authorization rule loaded from RBAC API permissions.
 */
public record ApiPermissionRule(
        String permissionCode,
        String httpMethod,
        String urlPattern,
        String matcherType,
        boolean publicFlag
) {
}
