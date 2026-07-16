package top.egon.mario.investment.marketdata.provider;

import top.egon.mario.investment.common.model.ProductType;
import top.egon.mario.investment.marketdata.provider.model.ExternalPositionTier;

import java.util.List;

/**
 * Supplies normalized position tiers used by margin and liquidation calculations.
 */
public interface PositionTierProvider extends MarketDataProvider {

    List<ExternalPositionTier> positionTiers(ProductType productType, String symbol);
}
