package top.egon.mario.agent.externalim.model;

public record ExternalReplyCommand(
        String connectorId,
        String conversationId,
        String sourceMessageId,
        String audienceKey,
        int replyVersion,
        String text
) {
}
