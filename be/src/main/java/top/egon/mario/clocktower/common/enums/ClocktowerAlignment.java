package top.egon.mario.clocktower.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import top.egon.mario.common.enums.CodedEnum;

import java.util.Map;

@Getter
public enum ClocktowerAlignment implements CodedEnum {
    GOOD(1, "善良"),
    EVIL(2, "邪恶"),
    NEUTRAL(3, "中立");

    private final int code;
    private final String desc;

    ClocktowerAlignment(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @JsonValue
    public Map<String, Object> toJson() {
        return ClocktowerEnumJsonSupport.toJson(this);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ClocktowerAlignment fromJson(Object input) {
        return ClocktowerEnumJsonSupport.fromJson(ClocktowerAlignment.class, input);
    }
}
