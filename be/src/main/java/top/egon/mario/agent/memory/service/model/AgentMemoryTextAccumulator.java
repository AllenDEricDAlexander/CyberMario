package top.egon.mario.agent.memory.service.model;

import org.springframework.util.StringUtils;

/**
 * Accumulates model stream text that may arrive as deltas or cumulative full text.
 */
public class AgentMemoryTextAccumulator {

    private String content = "";

    public void accept(String chunk) {
        content = merge(content, chunk);
    }

    public void acceptSnapshot(String snapshot) {
        content = snapshot == null ? "" : snapshot;
    }

    public String content() {
        return content;
    }

    public String normalizedContent() {
        return StringUtils.hasText(content) ? content : null;
    }

    public static String merge(String currentText, String chunkText) {
        String current = currentText == null ? "" : currentText;
        String chunk = chunkText == null ? "" : chunkText;
        if (chunk.isEmpty() || (!StringUtils.hasText(current) && !StringUtils.hasText(chunk))) {
            return current;
        }
        if (current.isEmpty() || (chunk.length() > current.length() && chunk.startsWith(current))) {
            return chunk;
        }
        return current + chunk;
    }
}
