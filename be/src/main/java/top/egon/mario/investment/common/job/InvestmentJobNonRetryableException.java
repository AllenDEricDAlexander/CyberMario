package top.egon.mario.investment.common.job;

/**
 * Invalid input or permanent dependency failure that must terminate immediately.
 */
public class InvestmentJobNonRetryableException extends RuntimeException {

    private final String errorCode;

    public InvestmentJobNonRetryableException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public InvestmentJobNonRetryableException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
