package top.egon.mario.pojo.response;

/**
 * Response returned by the chat API after an agent turn completes.
 */
public record ChatResponse(String threadId, String message) {
}
