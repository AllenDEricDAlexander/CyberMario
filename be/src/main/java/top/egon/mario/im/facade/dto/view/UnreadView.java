package top.egon.mario.im.facade.dto.view;

public record UnreadView(
        Long conversationId,
        Long userId,
        Long lastReadSeq,
        Long unreadCount) {
}
