package top.egon.mario.rbac.dto.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import top.egon.mario.common.enums.CodedEnum;

import java.util.Map;

/**
 * Lifecycle status for permissions.
 */
@Getter
public enum PermissionStatus implements CodedEnum {
    DISABLED(0, "禁用"),
    ENABLED(1, "启用"),
    DRAFT(2, "草稿");

    private final int code;
    private final String desc;

    PermissionStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @JsonValue
    public Map<String, Object> toJson() {
        return EnumJsonSupport.toJson(this);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static PermissionStatus fromJson(Object input) {
        return EnumJsonSupport.fromJson(PermissionStatus.class, input);
    }
}
