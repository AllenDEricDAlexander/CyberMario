package top.egon.mario.investment.common;

import java.util.Objects;

/**
 * Business exception raised by Investment services.
 */
public class InvestmentException extends RuntimeException {

    private final InvestmentErrorCode errorCode;

    public InvestmentException(InvestmentErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public InvestmentException(InvestmentErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public InvestmentErrorCode getErrorCode() {
        return errorCode;
    }

    public String getCode() {
        return errorCode.code();
    }
}
