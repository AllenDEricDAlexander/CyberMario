package top.egon.mario.rbac.po.enums;

import lombok.Getter;
import top.egon.mario.common.enums.CodedEnum;

/**
 * Relationship meaning between a button and an API permission.
 */
@Getter
public enum ButtonApiRelationType implements CodedEnum {
    CALLS(1, "调用"),
    REQUIRES(2, "依赖"),
    SUGGESTED(3, "建议");

    private final int code;
    private final String desc;

    ButtonApiRelationType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

}
