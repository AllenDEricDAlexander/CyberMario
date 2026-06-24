package top.egon.mario.clocktower.chat.dto;

import jakarta.validation.constraints.NotNull;

public record ClocktowerChatPrivateConversationRequest(
        @NotNull Long roomId,
        @NotNull Long targetUserId
) {
}
