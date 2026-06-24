package top.egon.mario.im.context;

public record ImContext(
        String contextType,
        Long contextId,
        Long channelId,
        Long groupId,
        Long conversationId,
        String scopeType,
        Long scopeId,
        String conversationType,
        String participantKey,
        ImPrincipal principal,
        boolean activeConversationMember) {
}
