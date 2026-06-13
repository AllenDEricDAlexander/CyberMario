package top.egon.mario.rbac.po.enums;

import lombok.Getter;
import top.egon.mario.rbac.common.CodedEnum;

/**
 * URL pattern matcher type for API permission rules.
 */
@Getter
public enum ApiMatcherType implements CodedEnum {
    EXACT(1, "精确匹配"),
    MVC(2, "MVC匹配"),
    ANT(3, "Ant匹配"),
    REGEX(4, "正则匹配");

    private final int code;
    private final String desc;

    ApiMatcherType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

}
