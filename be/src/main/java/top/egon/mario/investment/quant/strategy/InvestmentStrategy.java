package top.egon.mario.investment.quant.strategy;

/**
 * Java-only Investment strategy SPI; strategy rules and parameters live in source code.
 */
public interface InvestmentStrategy {

    StrategyDescriptor descriptor();

    StrategyDecision evaluate(StrategyContext context);
}
