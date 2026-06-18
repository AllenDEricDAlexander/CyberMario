package top.egon.mario.clocktower.script.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import top.egon.mario.clocktower.common.enums.ClocktowerAlignment;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.po.ClocktowerRolePo;

public record ClocktowerRoleSummaryResponse(
        ClocktowerScriptCode scriptCode,
        String roleCode,
        String roleName,
        ClocktowerRoleType roleType,
        ClocktowerAlignment alignment,
        @JsonIgnore
        String abilityText,
        @JsonIgnore
        int complexity,
        @JsonIgnore
        boolean firstNight,
        @JsonIgnore
        boolean otherNight,
        @JsonIgnore
        boolean setupModifier
) {

    public ClocktowerRoleSummaryResponse(ClocktowerScriptCode scriptCode, String roleCode, String roleName,
                                         ClocktowerRoleType roleType, ClocktowerAlignment alignment) {
        this(scriptCode, roleCode, roleName, roleType, alignment, "", 1, false, false, false);
    }

    public static ClocktowerRoleSummaryResponse from(ClocktowerRolePo role) {
        return new ClocktowerRoleSummaryResponse(role.getScriptCode(), role.getRoleCode(), role.getName(),
                role.getRoleType(), role.getAlignment(), role.getAbilityText(), role.getComplexity(),
                role.isFirstNight(), role.isOtherNight(), role.isSetupModifier());
    }
}
