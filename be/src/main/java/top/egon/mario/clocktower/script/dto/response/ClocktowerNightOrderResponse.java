package top.egon.mario.clocktower.script.dto.response;

import top.egon.mario.clocktower.common.enums.ClocktowerNightType;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.po.ClocktowerNightOrderPo;
import top.egon.mario.clocktower.script.po.ClocktowerRolePo;

public record ClocktowerNightOrderResponse(
        ClocktowerScriptCode scriptCode,
        String roleCode,
        String roleName,
        ClocktowerRoleType roleType,
        ClocktowerNightType nightType,
        int orderNo,
        int sortOrder,
        String reminderText
) {

    public static ClocktowerNightOrderResponse from(ClocktowerNightOrderPo order, ClocktowerRolePo role) {
        return new ClocktowerNightOrderResponse(order.getScriptCode(), order.getRoleCode(),
                role == null ? order.getRoleCode() : role.getName(),
                role == null ? null : role.getRoleType(),
                order.getNightType(), order.getOrderNo(), order.getSortOrder(),
                order.getReminderText());
    }
}
