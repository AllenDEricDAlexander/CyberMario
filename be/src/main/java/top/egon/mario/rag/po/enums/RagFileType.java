package top.egon.mario.rag.po.enums;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Supported RAG source file type.
 */
public enum RagFileType {

    MD,
    TXT,
    PDF,
    DOCX,
    TEXT;

    /**
     * Resolves the supported file type from a filename.
     */
    public static RagFileType fromFilename(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return TEXT;
        }
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "md", "markdown" -> MD;
            case "txt" -> TXT;
            case "pdf" -> PDF;
            case "docx" -> DOCX;
            default -> TEXT;
        };
    }

}
