package top.egon.mario.rbac.dto.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import top.egon.mario.rbac.common.CodedEnum;

import java.util.Map;

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

    @JsonValue
    public Map<String, Object> toJson() {
        return EnumJsonSupport.toJson(this);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static RbacStatus fromJson(Object input) {
        return EnumJsonSupport.fromJson(RbacStatus.class, input);
    }
}
