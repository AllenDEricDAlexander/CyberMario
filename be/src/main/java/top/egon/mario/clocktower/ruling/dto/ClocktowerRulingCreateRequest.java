package top.egon.mario.clocktower.ruling.dto;

import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingReason;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingType;
import top.egon.mario.clocktower.common.enums.ClocktowerVisibility;

public record ClocktowerRulingCreateRequest(
        ClocktowerRulingType rulingType,
        Long targetSeatId,
        Long nominationId,
        ClocktowerPhase targetPhase,
        String publicLifeStatus,
        String winner,
        ClocktowerRulingReason reason,
        String note,
        String publicNote,
        ClocktowerVisibility visibility,
        boolean force
) {
}
