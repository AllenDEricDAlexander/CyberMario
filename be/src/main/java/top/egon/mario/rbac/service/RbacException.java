package top.egon.mario.rbac.service;

/**
 * Business exception used by RBAC services to expose stable error codes.
 */
public class RbacException extends RuntimeException {

    private final String code;
    private final String detailMessage;

    public RbacException(String code, String message) {
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
