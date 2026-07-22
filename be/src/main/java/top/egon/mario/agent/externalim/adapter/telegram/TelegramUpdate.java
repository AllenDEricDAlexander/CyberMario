package top.egon.mario.agent.externalim.adapter.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.util.StringUtils;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdate(
        @JsonProperty("update_id") Long updateId,
        TelegramMessage message
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramMessage(
            @JsonProperty("message_id") Long messageId,
            Long date,
            TelegramUser from,
            TelegramChat chat,
            String text,
            @JsonProperty("reply_to_message") TelegramMessage replyToMessage
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramUser(
            Long id,
            @JsonProperty("is_bot") boolean bot,
            String username,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName
    ) {
        public String displayName() {
            return Stream.of(firstName, lastName)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.joining(" "));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramChat(Long id, String type, String title) {
    }
}
