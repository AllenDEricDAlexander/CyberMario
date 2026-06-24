package top.egon.mario.clocktower.view.dto;

import top.egon.mario.clocktower.chat.dto.ClocktowerChatConversationResponse;
import top.egon.mario.clocktower.common.enums.ClocktowerViewerMode;

import java.util.List;

public record ClocktowerGameViewResponse(
        Long gameId,
        Long roomId,
        int gameNo,
        String status,
        String phase,
        ClocktowerViewerMode viewerMode,
        ClocktowerGameSeatViewResponse mySeat,
        List<ClocktowerGameSeatViewResponse> publicSeats,
        List<ClocktowerGameSeatViewResponse> grimoire,
        List<AvailableActionResponse> availableActions,
        List<ClocktowerGameEventResponse> events,
        List<ClocktowerChatConversationResponse> conversations
) {
}
