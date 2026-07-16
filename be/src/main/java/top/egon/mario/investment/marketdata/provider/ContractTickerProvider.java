package top.egon.mario.investment.marketdata.provider;

import top.egon.mario.investment.common.model.ProductType;
import top.egon.mario.investment.marketdata.provider.model.ExternalContractTicker;

import java.util.List;
import java.util.Set;

/**
 * Supplies normalized latest contract prices.
 */
public interface ContractTickerProvider extends MarketDataProvider {

    List<ExternalContractTicker> tickers(ProductType productType, Set<String> symbols);
}
