package top.egon.mario.investment.common;

/**
 * Stable error codes returned by Investment APIs.
 */
public enum InvestmentErrorCode {
    INVALID_REQUEST,
    NOT_FOUND,
    FORBIDDEN,
    CONFLICT,
    DATA_UNAVAILABLE,
    CAPABILITY_UNAVAILABLE,
    SUBSCRIPTION_REJECTED,
    RISK_REJECTED,
    INTERNAL_ERROR;

    public String code() {
        return "INVESTMENT_" + name();
    }
}
