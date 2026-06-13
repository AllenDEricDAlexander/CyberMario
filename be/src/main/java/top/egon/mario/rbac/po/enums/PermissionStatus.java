package top.egon.mario.rbac.po.enums;

import lombok.Getter;
import top.egon.mario.rbac.common.CodedEnum;

/**
 * Lifecycle status for permissions.
 */
@Getter
public enum PermissionStatus implements CodedEnum {
    DISABLED(0, "禁用"),
    ENABLED(1, "启用"),
    DRAFT(2, "草稿");

    private final int code;
    private final String desc;

    PermissionStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

}
