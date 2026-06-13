package top.egon.mario.rbac.po;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;

/**
 * Common enablement status for users and roles.
 */
public enum RbacStatus implements CodedEnum {
    DISABLED(0, "禁用"),
    ENABLED(1, "启用");

    private final int code;
    private final String desc;

    RbacStatus(int code, String desc) {
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
    public static RbacStatus fromJson(Object input) {
        return CodedEnum.fromJson(RbacStatus.class, input);
    }
}
