package top.egon.mario.rbac.po.enums;

import lombok.Getter;
import top.egon.mario.rbac.common.CodedEnum;

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

}
