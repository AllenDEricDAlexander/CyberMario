package top.egon.mario.investment.quant.backtest.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record FundingPoint(Instant fundingTime, BigDecimal rate) {

    public FundingPoint {
        Objects.requireNonNull(fundingTime, "fundingTime");
        Objects.requireNonNull(rate, "rate");
    }
}
