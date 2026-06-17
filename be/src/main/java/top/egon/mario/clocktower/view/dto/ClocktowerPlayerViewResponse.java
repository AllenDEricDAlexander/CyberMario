package top.egon.mario.clocktower.view.dto;

import top.egon.mario.clocktower.common.enums.ClocktowerViewerMode;
import top.egon.mario.clocktower.event.dto.ClocktowerEventResponse;
import top.egon.mario.clocktower.grimoire.dto.response.GamePhaseResponse;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;

import java.util.List;

public record ClocktowerPlayerViewResponse(
        ClocktowerRoomResponse room,
        ClocktowerViewerMode viewerMode,
        PlayerSeatViewResponse mySeat,
        List<PublicSeatResponse> publicSeats,
        GamePhaseResponse phase,
        List<AvailableActionResponse> availableActions,
        List<ClocktowerEventResponse> recentEvents,
        List<PrivateThreadSummaryResponse> privateThreads
) {
}
