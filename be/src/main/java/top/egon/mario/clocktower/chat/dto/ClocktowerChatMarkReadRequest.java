package top.egon.mario.clocktower.chat.dto;

import jakarta.validation.constraints.NotNull;

public record ClocktowerChatMarkReadRequest(
        @NotNull Long messageSeq
) {
}
