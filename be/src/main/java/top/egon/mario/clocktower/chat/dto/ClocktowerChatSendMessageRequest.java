package top.egon.mario.clocktower.chat.dto;

import jakarta.validation.constraints.NotBlank;

public record ClocktowerChatSendMessageRequest(
        @NotBlank String content,
        String metadataJson
) {
}
