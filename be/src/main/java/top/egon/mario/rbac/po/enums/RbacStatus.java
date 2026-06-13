package top.egon.mario.rbac.po.enums;

import lombok.Getter;
import top.egon.mario.rbac.common.CodedEnum;

/**
 * Common enablement status for users and roles.
 */
@Getter
public enum RbacStatus implements CodedEnum {
    DISABLED(0, "禁用"),
    ENABLED(1, "启用");

    private final int code;
    private final String desc;

    RbacStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

}
