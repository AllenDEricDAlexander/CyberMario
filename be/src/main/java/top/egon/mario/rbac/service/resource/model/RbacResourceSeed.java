package top.egon.mario.rbac.service.resource.model;

import top.egon.mario.rbac.po.enums.PermissionStatus;
import top.egon.mario.rbac.po.enums.PermissionType;

import java.util.List;

/**
 * Normalized resource declaration consumed by the RBAC resource synchronizer.
 */
public record RbacResourceSeed(
        String appCode,
        String serviceTag,
        String code,
        PermissionType type,
        String name,
        String parentCode,
        PermissionStatus status,
        int sortNo,
        String description,
        RbacMenuSeed menu,
        RbacButtonSeed button,
        RbacApiSeed api,
        List<String> buttonApiCodes,
        RbacResourceSource source
) {

    public RbacResourceSeed {
        buttonApiCodes = buttonApiCodes == null ? List.of() : List.copyOf(buttonApiCodes);
    }

    public static RbacResourceSeed menu(String appCode, String serviceTag, String code, String name, String parentCode,
                                        PermissionStatus status, int sortNo, String description,
                                        RbacMenuSeed menu, RbacResourceSource source) {
        return new RbacResourceSeed(appCode, serviceTag, code, PermissionType.MENU, name, parentCode, status, sortNo,
                description, menu, null, null, List.of(), source);
    }

    public static RbacResourceSeed button(String appCode, String serviceTag, String code, String name, String parentCode,
                                          PermissionStatus status, int sortNo, String description,
                                          RbacButtonSeed button, List<String> buttonApiCodes,
                                          RbacResourceSource source) {
        return new RbacResourceSeed(appCode, serviceTag, code, PermissionType.BUTTON, name, parentCode, status, sortNo,
                description, null, button, null, buttonApiCodes, source);
    }

    public static RbacResourceSeed api(String appCode, String serviceTag, String code, String name,
                                       PermissionStatus status, int sortNo, String description,
                                       RbacApiSeed api, RbacResourceSource source) {
        return new RbacResourceSeed(appCode, serviceTag, code, PermissionType.API, name, null, status, sortNo,
                description, null, null, api, List.of(), source);
    }

}
