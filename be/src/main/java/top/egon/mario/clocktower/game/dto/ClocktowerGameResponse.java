package top.egon.mario.clocktower.game.dto;

import top.egon.mario.clocktower.game.po.ClocktowerGamePo;

import java.util.List;

public record ClocktowerGameResponse(
        Long gameId,
        Long roomId,
        int gameNo,
        String status,
        String phase,
        List<ClocktowerGameConversationResponse> conversations
) {

    public static ClocktowerGameResponse from(ClocktowerGamePo game,
                                              List<ClocktowerGameConversationResponse> conversations) {
        return new ClocktowerGameResponse(game.getId(), game.getRoomId(), game.getGameNo(), game.getStatus(),
                game.getPhase(), conversations == null ? List.of() : conversations);
    }
}
