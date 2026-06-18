package top.egon.mario.clocktower.script.dto.response;

import top.egon.mario.clocktower.common.enums.ClocktowerAlignment;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.po.ClocktowerRolePo;

public record ClocktowerRoleSummaryResponse(
        ClocktowerScriptCode scriptCode,
        String roleCode,
        String roleName,
        ClocktowerRoleType roleType,
        ClocktowerAlignment alignment
) {

    public static ClocktowerRoleSummaryResponse from(ClocktowerRolePo role) {
        return new ClocktowerRoleSummaryResponse(role.getScriptCode(), role.getRoleCode(), role.getName(),
                role.getRoleType(), role.getAlignment());
    }
}
