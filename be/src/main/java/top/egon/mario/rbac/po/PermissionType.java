package top.egon.mario.rbac.po;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;

/**
 * Permission resource type used by RBAC authorization.
 */
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
    public static PermissionType fromJson(Object input) {
        return CodedEnum.fromJson(PermissionType.class, input);
    }
}
