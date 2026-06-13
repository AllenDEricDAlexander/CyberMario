package top.egon.mario.rag.service;

import top.egon.mario.rag.po.RagFileObjectPo;

/**
 * Extracts plain text from supported RAG document formats.
 */
public interface RagDocumentParser {

    /**
     * Extracts text from a stored file object.
     */
    String parse(RagFileObjectPo fileObject);

}
