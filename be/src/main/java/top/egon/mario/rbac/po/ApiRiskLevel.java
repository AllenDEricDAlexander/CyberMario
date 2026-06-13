package top.egon.mario.rbac.po;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;

/**
 * Risk level for audit and operator awareness.
 */
public enum ApiRiskLevel implements CodedEnum {
    LOW(1, "低"),
    MEDIUM(2, "中"),
    HIGH(3, "高");

    private final int code;
    private final String desc;

    ApiRiskLevel(int code, String desc) {
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
    public static ApiRiskLevel fromJson(Object input) {
        return CodedEnum.fromJson(ApiRiskLevel.class, input);
    }
}
