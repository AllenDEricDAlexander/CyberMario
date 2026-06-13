package top.egon.mario.rbac.dto.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import top.egon.mario.rbac.common.CodedEnum;

import java.util.Map;

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

    @JsonValue
    public Map<String, Object> toJson() {
        return EnumJsonSupport.toJson(this);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static PermissionType fromJson(Object input) {
        return EnumJsonSupport.fromJson(PermissionType.class, input);
    }
}
