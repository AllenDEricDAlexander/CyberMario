package top.egon.mario.rbac.service.model;

import top.egon.mario.rbac.po.enums.ApiMatcherType;

/**
 * Runtime API authorization rule loaded from RBAC API permissions.
 */
public record ApiPermissionRule(
        String permissionCode,
        String httpMethod,
        String urlPattern,
        ApiMatcherType matcherType,
        boolean publicFlag
) {

    public ApiPermissionRule(String permissionCode, String httpMethod, String urlPattern, String matcherType,
                             boolean publicFlag) {
        this(permissionCode, httpMethod, urlPattern, ApiMatcherType.valueOf(matcherType), publicFlag);
    }
}
