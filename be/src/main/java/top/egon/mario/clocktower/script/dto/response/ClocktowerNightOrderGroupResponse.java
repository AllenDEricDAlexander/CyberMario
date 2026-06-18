package top.egon.mario.clocktower.script.dto.response;

import java.util.List;

public record ClocktowerNightOrderGroupResponse(
        List<ClocktowerNightOrderResponse> firstNight,
        List<ClocktowerNightOrderResponse> otherNight
) {
}
