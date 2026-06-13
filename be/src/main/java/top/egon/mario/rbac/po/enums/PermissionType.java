package top.egon.mario.rbac.po.enums;

import lombok.Getter;
import top.egon.mario.common.enums.CodedEnum;

/**
 * Permission resource type used by RBAC authorization.
 */
@Getter
public enum PermissionType implements CodedEnum {
    MENU(1, "菜单"),
    BUTTON(2, "按钮"),
    API(3, "接口");

    private final int code;
    private final String desc;

    PermissionType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

}
