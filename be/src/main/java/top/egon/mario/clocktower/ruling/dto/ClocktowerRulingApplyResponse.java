package top.egon.mario.clocktower.ruling.dto;

import top.egon.mario.clocktower.event.dto.ClocktowerEventResponse;
import top.egon.mario.clocktower.grimoire.dto.response.ClocktowerGrimoireResponse;

import java.util.List;

public record ClocktowerRulingApplyResponse(
        ClocktowerRulingResponse ruling,
        ClocktowerGrimoireResponse grimoire,
        List<ClocktowerEventResponse> events
) {
}
