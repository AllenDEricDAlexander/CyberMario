package top.egon.mario.investment.quant.backtest.model;

import top.egon.mario.investment.portfolio.margin.PositionTier;
import top.egon.mario.investment.trading.matching.model.ContractTerms;
import top.egon.mario.investment.trading.matching.model.FuturesBar;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record BacktestInstrumentInput(
        long instrumentId,
        ContractTerms contractTerms,
        BigDecimal makerFeeRate,
        BigDecimal takerFeeRate,
        BigDecimal leverage,
        List<PositionTier> positionTiers,
        List<FuturesBar> signalBars,
        List<FuturesBar> marketBars,
        List<FuturesBar> markBars,
        List<FundingPoint> fundingPoints
) {
    public BacktestInstrumentInput {
        if (instrumentId <= 0) {
            throw new IllegalArgumentException("instrumentId must be positive");
        }
        Objects.requireNonNull(contractTerms, "contractTerms");
        if (makerFeeRate == null || makerFeeRate.signum() < 0
                || takerFeeRate == null || takerFeeRate.signum() < 0) {
            throw new IllegalArgumentException("fee rates must not be negative");
        }
        if (leverage == null || leverage.signum() <= 0) {
            throw new IllegalArgumentException("leverage must be positive");
        }
        positionTiers = List.copyOf(positionTiers);
        signalBars = List.copyOf(signalBars);
        marketBars = List.copyOf(marketBars);
        markBars = List.copyOf(markBars);
        fundingPoints = List.copyOf(fundingPoints);
        if (marketBars.size() < 2 || signalBars.size() != marketBars.size()
                || markBars.size() != marketBars.size()) {
            throw new IllegalArgumentException("signal, market and mark bars must be aligned and contain two bars");
        }
        for (int index = 0; index < marketBars.size(); index++) {
            if (!marketBars.get(index).openTime().equals(signalBars.get(index).openTime())
                    || !marketBars.get(index).closeTime().equals(signalBars.get(index).closeTime())
                    || !marketBars.get(index).openTime().equals(markBars.get(index).openTime())
                    || !marketBars.get(index).closeTime().equals(markBars.get(index).closeTime())) {
                throw new IllegalArgumentException("signal, market and mark bars must share timestamps");
            }
        }
    }
}
