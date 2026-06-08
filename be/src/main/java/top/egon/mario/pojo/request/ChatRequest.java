package top.egon.mario.pojo.request;

/**
 * Request body for an agent conversation turn.
 */
public record ChatRequest(String message, String threadId) {
}
