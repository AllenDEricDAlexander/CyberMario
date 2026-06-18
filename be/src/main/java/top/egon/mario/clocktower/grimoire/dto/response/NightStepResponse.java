package top.egon.mario.clocktower.grimoire.dto.response;

import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;

public record NightStepResponse(
        int orderNo,
        Long seatId,
        String roleCode,
        String roleName,
        ClocktowerRoleType roleType,
        boolean wakeRequired,
        String skipReason,
        boolean completed
) {
}
