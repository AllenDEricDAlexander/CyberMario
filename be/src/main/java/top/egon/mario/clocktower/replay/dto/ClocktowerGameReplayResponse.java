package top.egon.mario.clocktower.replay.dto;

import top.egon.mario.clocktower.common.enums.ClocktowerViewerMode;
import top.egon.mario.clocktower.view.dto.ClocktowerGameEventResponse;

import java.util.List;

public record ClocktowerGameReplayResponse(
        Long gameId,
        Long roomId,
        ClocktowerViewerMode viewerMode,
        List<ClocktowerGameEventResponse> events
) {
}
