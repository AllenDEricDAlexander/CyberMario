package top.egon.mario.investment.common.job;

/**
 * Dependency or transient failure that is eligible for bounded retry.
 */
public class InvestmentJobRetryableException extends RuntimeException {

    private final String errorCode;

    public InvestmentJobRetryableException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public InvestmentJobRetryableException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
