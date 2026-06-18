package top.egon.mario.clocktower.script.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import top.egon.mario.clocktower.common.enums.ClocktowerAlignment;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.po.ClocktowerRolePo;

public record ClocktowerRoleResponse(
        ClocktowerScriptCode scriptCode,
        String roleCode,
        String roleName,
        ClocktowerRoleType roleType,
        ClocktowerAlignment alignment,
        String abilityText,
        Integer firstNightOrder,
        Integer otherNightOrder,
        String firstNightReminder,
        String otherNightReminder,
        boolean enabled,
        String sourceUrl
) {

    public static ClocktowerRoleResponse from(ClocktowerRolePo role) {
        return new ClocktowerRoleResponse(role.getScriptCode(), role.getRoleCode(), role.getName(), role.getRoleType(),
                role.getAlignment(), role.getAbilityText(), role.getFirstNightOrder(),
                role.getOtherNightOrder(), role.getFirstNightReminder(), role.getOtherNightReminder(),
                role.isEnabled(), role.getSourceUrl());
    }

    @JsonProperty("name")
    public String name() {
        return roleName;
    }
}
