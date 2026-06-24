package top.egon.mario.clocktower.admin.dto;

import top.egon.mario.clocktower.chat.dto.ClocktowerChatConversationResponse;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.view.dto.ClocktowerGameEventResponse;
import top.egon.mario.clocktower.view.dto.ClocktowerGameSeatViewResponse;

import java.time.Instant;
import java.util.List;

public record ClocktowerGameAuditResponse(
        Long gameId,
        Long roomId,
        int gameNo,
        String scriptCode,
        String status,
        String phase,
        Instant startedAt,
        Instant endedAt,
        List<ClocktowerGameSeatViewResponse> seats,
        List<ClocktowerGameEventResponse> events,
        List<ClocktowerChatConversationResponse> conversations
) {

    public static ClocktowerGameAuditResponse from(ClocktowerGamePo game,
                                                   List<ClocktowerGameSeatViewResponse> seats,
                                                   List<ClocktowerGameEventResponse> events,
                                                   List<ClocktowerChatConversationResponse> conversations) {
        return new ClocktowerGameAuditResponse(game.getId(), game.getRoomId(), game.getGameNo(),
                game.getScriptCode(), game.getStatus(), game.getPhase(), game.getStartedAt(), game.getEndedAt(),
                seats, events, conversations);
    }
}
