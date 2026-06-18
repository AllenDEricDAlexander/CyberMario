package top.egon.mario.clocktower.ruling.dto;

import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingReason;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingStatus;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingType;
import top.egon.mario.clocktower.common.enums.ClocktowerVisibility;
import top.egon.mario.clocktower.ruling.po.ClocktowerRulingPo;

public record ClocktowerRulingResponse(
        Long rulingId,
        Long roomId,
        ClocktowerRulingType rulingType,
        ClocktowerRulingStatus status,
        Long targetSeatId,
        Long nominationId,
        ClocktowerPhase targetPhase,
        String publicLifeStatus,
        String winner,
        ClocktowerRulingReason reason,
        String note,
        String publicNote,
        ClocktowerVisibility visibility,
        Long undoOfRulingId
) {

    public static ClocktowerRulingResponse from(ClocktowerRulingPo ruling) {
        return new ClocktowerRulingResponse(ruling.getId(), ruling.getRoomId(), ruling.getRulingType(),
                ruling.getStatus(), ruling.getTargetSeatId(), ruling.getNominationId(), ruling.getTargetPhase(),
                ruling.getPublicLifeStatus(), ruling.getWinner(), ruling.getReason(), ruling.getNote(),
                ruling.getPublicNote(), ruling.getVisibility(), ruling.getUndoOfRulingId());
    }
}
