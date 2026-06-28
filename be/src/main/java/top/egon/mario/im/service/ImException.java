package top.egon.mario.im.service;

public class ImException extends RuntimeException {

    private final String code;
    private final String detailMessage;

    public ImException(String code) {
        this(code, code);
    }

    public ImException(String code, String message) {
        super(code + ": " + message);
        this.code = code;
        this.detailMessage = message;
    }

    public String getCode() {
        return code;
    }

    public String getDetailMessage() {
        return detailMessage;
    }
}
