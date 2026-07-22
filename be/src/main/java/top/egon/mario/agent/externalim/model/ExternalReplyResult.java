package top.egon.mario.agent.externalim.model;

public record ExternalReplyResult(
        boolean sent,
        String platformMessageId,
        boolean retryable,
        String errorCode,
        String errorMessage
) {

    public static ExternalReplyResult sent(String platformMessageId) {
        return new ExternalReplyResult(true, platformMessageId, false, null, null);
    }

    public static ExternalReplyResult failed(boolean retryable, String code, String message) {
        return new ExternalReplyResult(false, null, retryable, code, message);
    }
}
