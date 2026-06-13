package top.egon.mario.rag.service.impl;

import org.apache.tika.Tika;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import top.egon.mario.rag.po.RagFileObjectPo;
import top.egon.mario.rag.po.enums.RagFileType;
import top.egon.mario.rag.service.RagDocumentParser;
import top.egon.mario.rag.service.RagException;
import top.egon.mario.rag.storage.RagFileStorage;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Tika-backed parser for PDF/DOCX and direct text reader for MD/TXT files.
 */
@Service
public class TikaRagDocumentParser implements RagDocumentParser {

    private final RagFileStorage fileStorage;
    private final Tika tika = new Tika();

    public TikaRagDocumentParser(RagFileStorage fileStorage) {
        this.fileStorage = fileStorage;
    }

    @Override
    public String parse(RagFileObjectPo fileObject) {
        Resource resource = fileStorage.load(fileObject.getStorageKey());
        try (InputStream inputStream = resource.getInputStream()) {
            if (fileObject.getFileType() == RagFileType.MD || fileObject.getFileType() == RagFileType.TXT) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            return tika.parseToString(inputStream);
        } catch (Exception e) {
            throw new RagException("RAG_DOCUMENT_PARSE_FAILED", "document parse failed");
        }
    }

}
