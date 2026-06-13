package top.egon.mario.rbac.po;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;

/**
 * Relationship meaning between a button and an API permission.
 */
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
    public static ButtonApiRelationType fromJson(Object input) {
        return CodedEnum.fromJson(ButtonApiRelationType.class, input);
    }
}
