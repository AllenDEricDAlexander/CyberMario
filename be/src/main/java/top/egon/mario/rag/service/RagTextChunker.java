package top.egon.mario.rag.service;
import top.egon.mario.rag.service.model.RagChunkCandidate;

import java.util.List;

/**
 * Splits extracted document text into searchable chunks.
 */
public interface RagTextChunker {

    /**
     * Splits text into chunk candidates.
     */
    List<RagChunkCandidate> split(String text);

}
