package top.egon.mario.pojo.response;

/**
 * Streaming chunk emitted during an agent conversation turn.
 *
 * @param threadId conversation identifier
 * @param message  text content of this chunk (think, final message, or stream error)
 * @param type     chunk type: "think" for reasoning content, "message" for final output, "error" for stream failures
 */
public record ChatResponse(String threadId, String message, String type) {

    /**
     * Convenience constructor defaulting type to "message".
     */
    public ChatResponse(String threadId, String message) {
        this(threadId, message, "message");
    }
}
