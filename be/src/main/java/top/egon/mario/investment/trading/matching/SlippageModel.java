package top.egon.mario.investment.trading.matching;

import top.egon.mario.investment.trading.matching.model.TradeSide;

import java.math.BigDecimal;

/**
 * Applies a deterministic execution-price adjustment without reading runtime state.
 */
public interface SlippageModel {

    BigDecimal apply(BigDecimal referencePrice, TradeSide tradeSide);
}
