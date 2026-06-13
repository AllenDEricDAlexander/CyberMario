package top.egon.mario.rbac.service;

/**
 * Business exception used by RBAC services to expose stable error codes.
 */
public class RbacException extends RuntimeException {

    private final String code;

    public RbacException(String code, String message) {
        super(code + ": " + message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

}
