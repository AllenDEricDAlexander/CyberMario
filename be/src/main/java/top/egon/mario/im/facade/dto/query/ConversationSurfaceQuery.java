package top.egon.mario.im.facade.dto.query;

public record ConversationSurfaceQuery(
        Long conversationId,
        String surfaceType,
        Long surfaceId) {
}
