package top.egon.mario.investment.marketdata.provider;

import top.egon.mario.investment.common.model.ProductType;
import top.egon.mario.investment.marketdata.provider.model.ExternalContract;

import java.util.List;
import java.util.Set;

/**
 * Supplies normalized contract metadata without exposing exchange DTOs.
 */
public interface ContractMetadataProvider extends MarketDataProvider {

    List<ExternalContract> contracts(ProductType productType, Set<String> symbols);
}
