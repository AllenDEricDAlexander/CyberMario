package top.egon.mario.investment.trading.matching;

import top.egon.mario.investment.trading.matching.model.ContractTerms;
import top.egon.mario.investment.trading.matching.model.FuturesBar;
import top.egon.mario.investment.trading.matching.model.MatchResult;
import top.egon.mario.investment.trading.matching.model.MatchingOrder;

/**
 * Deterministic order-matching strategy shared by backtest and paper trading.
 */
public interface MatchingModel {

    MatchResult match(MatchingOrder order, FuturesBar bar, ContractTerms contractTerms);
}
