package top.egon.mario.investment.marketdata.provider;

import top.egon.mario.investment.marketdata.provider.model.ExternalFundingRate;
import top.egon.mario.investment.marketdata.provider.model.FundingRateQuery;

import java.util.List;

/**
 * Supplies normalized contract funding rates.
 */
public interface FundingRateProvider extends MarketDataProvider {

    List<ExternalFundingRate> fundingRates(FundingRateQuery query);
}
