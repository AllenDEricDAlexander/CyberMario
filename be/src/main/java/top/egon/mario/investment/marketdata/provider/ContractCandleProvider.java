package top.egon.mario.investment.marketdata.provider;

import top.egon.mario.investment.marketdata.provider.model.CandleQuery;
import top.egon.mario.investment.marketdata.provider.model.ExternalCandle;

import java.util.List;

/**
 * Supplies normalized closed or in-progress contract candles.
 */
public interface ContractCandleProvider extends MarketDataProvider {

    List<ExternalCandle> candles(CandleQuery query);
}
