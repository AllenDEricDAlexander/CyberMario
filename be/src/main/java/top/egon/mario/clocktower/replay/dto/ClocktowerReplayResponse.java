package top.egon.mario.clocktower.replay.dto;

import top.egon.mario.clocktower.event.dto.ClocktowerEventResponse;

import java.util.List;

public record ClocktowerReplayResponse(
        Long roomId,
        String mode,
        List<ClocktowerEventResponse> events
) {
}
