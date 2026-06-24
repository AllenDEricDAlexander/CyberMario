package top.egon.mario.clocktower.chat;

public record ClocktowerChatAccessContext(
        ClocktowerChatViewerMode viewerMode,
        String groupKey,
        String conversationType,
        String phase,
        int dayNo,
        boolean activeConversationMember
) {
}
