package top.egon.mario.clocktower.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import top.egon.mario.common.enums.CodedEnum;

import java.util.Map;

@Getter
public enum ClocktowerNightType implements CodedEnum {
    FIRST_NIGHT(1, "首夜"),
    OTHER_NIGHT(2, "其他夜晚");

    private final int code;
    private final String desc;

    ClocktowerNightType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @JsonValue
    public Map<String, Object> toJson() {
        return ClocktowerEnumJsonSupport.toJson(this);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ClocktowerNightType fromJson(Object input) {
        return ClocktowerEnumJsonSupport.fromJson(ClocktowerNightType.class, input);
    }
}
