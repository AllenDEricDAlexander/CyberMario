package top.egon.mario.clocktower.game.dto;

public record ClocktowerGameConversationResponse(
        String groupKey,
        String conversationType,
        Long conversationId
) {
}
