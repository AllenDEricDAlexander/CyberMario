package top.egon.mario.agent.externalim.adapter.qq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
record QqOneBotEvent(
        Long time,
        @JsonProperty("self_id") Long selfId,
        @JsonProperty("post_type") String postType,
        @JsonProperty("message_type") String messageType,
        @JsonProperty("message_id") Long messageId,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("group_id") Long groupId,
        List<QqMessageSegment> message,
        QqSender sender
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record QqMessageSegment(String type, Map<String, Object> data) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record QqSender(
        @JsonProperty("user_id") Long userId,
        String nickname,
        String card
) {
}
