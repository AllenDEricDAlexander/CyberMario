package top.egon.mario.rbac.dto.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import top.egon.mario.rbac.common.CodedEnum;

import java.util.Map;

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

    @JsonValue
    public Map<String, Object> toJson() {
        return EnumJsonSupport.toJson(this);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ButtonApiRelationType fromJson(Object input) {
        return EnumJsonSupport.fromJson(ButtonApiRelationType.class, input);
    }
}
