package top.egon.mario.investment.marketdata.provider;

import java.util.Objects;

/**
 * Provider-neutral external failure that retains an actionable retry category.
 */
public class MarketDataProviderException extends RuntimeException {

    private final String providerCode;
    private final ProviderErrorCategory category;

    public MarketDataProviderException(String providerCode, ProviderErrorCategory category, String message) {
        super(message);
        this.providerCode = Objects.requireNonNull(providerCode, "providerCode");
        this.category = Objects.requireNonNull(category, "category");
    }

    public MarketDataProviderException(String providerCode, ProviderErrorCategory category, String message,
                                       Throwable cause) {
        super(message, cause);
        this.providerCode = Objects.requireNonNull(providerCode, "providerCode");
        this.category = Objects.requireNonNull(category, "category");
    }

    public String getProviderCode() {
        return providerCode;
    }

    public ProviderErrorCategory getCategory() {
        return category;
    }

    public boolean isRetryable() {
        return category == ProviderErrorCategory.RETRYABLE || category == ProviderErrorCategory.RATE_LIMITED;
    }
}
