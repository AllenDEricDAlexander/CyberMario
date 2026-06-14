package top.egon.mario.rag.service;

import jakarta.validation.constraints.NotNull;
import top.egon.mario.rag.po.RagFileObjectPo;

/**
 * Extracts plain text from supported RAG document formats.
 */
public interface RagDocumentParser {

    /**
     * Extracts text from a stored file object.
     */
    String parse(@NotNull RagFileObjectPo fileObject);

}
