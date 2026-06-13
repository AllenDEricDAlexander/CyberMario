package top.egon.mario.rbac.po;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;

/**
 * URL pattern matcher type for API permission rules.
 */
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

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }

    @JsonValue
    public Map<String, Object> toJson() {
        return CodedEnum.toJson(this);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ApiMatcherType fromJson(Object input) {
        return CodedEnum.fromJson(ApiMatcherType.class, input);
    }
}
