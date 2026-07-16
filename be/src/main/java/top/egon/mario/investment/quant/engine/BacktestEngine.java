package top.egon.mario.investment.quant.engine;

import top.egon.mario.investment.quant.backtest.model.BacktestInput;
import top.egon.mario.investment.quant.backtest.model.BacktestResult;

/**
 * Deterministic backtest engine boundary.
 */
public interface BacktestEngine {

    BacktestResult run(BacktestInput input);
}
