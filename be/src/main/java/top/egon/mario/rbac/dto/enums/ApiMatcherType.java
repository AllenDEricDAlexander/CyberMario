package top.egon.mario.rbac.dto.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import top.egon.mario.common.enums.CodedEnum;

import java.util.Map;

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

    @JsonValue
    public Map<String, Object> toJson() {
        return EnumJsonSupport.toJson(this);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ApiMatcherType fromJson(Object input) {
        return EnumJsonSupport.fromJson(ApiMatcherType.class, input);
    }
}
