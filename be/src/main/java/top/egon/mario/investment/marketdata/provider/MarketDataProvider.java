package top.egon.mario.investment.marketdata.provider;

import top.egon.mario.investment.common.model.DataCapability;

import java.util.Set;

/**
 * Common identity and capability contract for a normalized market-data adapter.
 */
public interface MarketDataProvider {

    String providerCode();

    Set<DataCapability> capabilities();
}
