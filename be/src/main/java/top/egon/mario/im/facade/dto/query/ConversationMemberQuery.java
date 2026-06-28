package top.egon.mario.im.facade.dto.query;

public record ConversationMemberQuery(
        Long conversationId,
        Long userId) {
}
