package top.egon.mario.rag.service;

/**
 * Business exception raised by RAG services.
 */
public class RagException extends RuntimeException {

    private final String code;

    public RagException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

}
