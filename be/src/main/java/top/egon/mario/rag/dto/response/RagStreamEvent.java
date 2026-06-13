package top.egon.mario.rag.dto.response;

import java.util.Map;

/**
 * JSON line event emitted by the RAG HTTP stream.
 */
public record RagStreamEvent(
        String type,
        Map<String, Object> data
) {
}
