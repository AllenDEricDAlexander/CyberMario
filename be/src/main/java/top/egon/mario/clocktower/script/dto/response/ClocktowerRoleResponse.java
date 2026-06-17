package top.egon.mario.clocktower.script.dto.response;

import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.po.ClocktowerRolePo;

public record ClocktowerRoleResponse(
        ClocktowerScriptCode scriptCode,
        String roleCode,
        ClocktowerRoleType roleType,
        String name,
        String abilityText,
        Integer firstNightOrder,
        Integer otherNightOrder,
        String firstNightReminder,
        String otherNightReminder,
        boolean enabled,
        String sourceUrl
) {

    public static ClocktowerRoleResponse from(ClocktowerRolePo role) {
        return new ClocktowerRoleResponse(role.getScriptCode(), role.getRoleCode(), role.getRoleType(),
                role.getName(), role.getAbilityText(), role.getFirstNightOrder(), role.getOtherNightOrder(),
                role.getFirstNightReminder(), role.getOtherNightReminder(), role.isEnabled(), role.getSourceUrl());
    }
}
