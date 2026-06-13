package top.egon.mario.rbac.dto.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import top.egon.mario.common.enums.CodedEnum;

import java.util.Map;

/**
 * Risk level for audit and operator awareness.
 */
@Getter
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

    @JsonValue
    public Map<String, Object> toJson() {
        return EnumJsonSupport.toJson(this);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ApiRiskLevel fromJson(Object input) {
        return EnumJsonSupport.fromJson(ApiRiskLevel.class, input);
    }
}
