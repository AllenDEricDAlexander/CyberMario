package top.egon.mario.investment.marketdata.provider;

/**
 * Stable failure categories that external adapters must map before returning to the module.
 */
public enum ProviderErrorCategory {
    RETRYABLE,
    NON_RETRYABLE,
    RATE_LIMITED,
    INVALID_DATA
}
