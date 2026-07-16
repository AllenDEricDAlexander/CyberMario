package top.egon.mario.investment.marketdata.subscription;

import java.util.Collection;

/**
 * Code-only source of approved market-data subscriptions.
 */
@FunctionalInterface
public interface InvestmentMarketSubscriptionProvider {

    Collection<MarketSubscription> subscriptions();
}
