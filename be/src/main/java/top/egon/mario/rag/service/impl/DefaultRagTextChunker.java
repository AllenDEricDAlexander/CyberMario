package top.egon.mario.rag.service.impl;

import org.springframework.stereotype.Component;
import top.egon.mario.rag.service.RagTextChunker;
import top.egon.mario.rag.service.model.RagChunkCandidate;

import java.util.ArrayList;
import java.util.List;

/**
 * Character-window chunker tuned for a stable first RAG implementation.
 */
@Component
public class DefaultRagTextChunker implements RagTextChunker {

    private static final int CHUNK_SIZE = 1800;
    private static final int OVERLAP = 200;

    @Override
    public List<RagChunkCandidate> split(String text) {
        String normalizedText = text == null ? "" : text.strip();
        if (normalizedText.isEmpty()) {
            return List.of();
        }
        List<RagChunkCandidate> chunks = new ArrayList<>();
        int index = 0;
        int offset = 0;
        while (offset < normalizedText.length()) {
            int end = Math.min(offset + CHUNK_SIZE, normalizedText.length());
            String content = normalizedText.substring(offset, end).strip();
            if (!content.isEmpty()) {
                chunks.add(new RagChunkCandidate(index++, content, estimateTokenCount(content)));
            }
            if (end == normalizedText.length()) {
                break;
            }
            offset = Math.max(end - OVERLAP, offset + 1);
        }
        return chunks;
    }

    private int estimateTokenCount(String content) {
        return Math.max(1, content.length() / 2);
    }

}
