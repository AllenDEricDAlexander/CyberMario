package top.egon.mario.investment.common.job;

import org.springframework.util.Assert;

import java.time.Instant;

/**
 * Normal handler outcome: finish now or wait for a known dependency time.
 */
public record InvestmentJobHandlerResult(String resultJson, Instant nextAvailableAt) {

    public InvestmentJobHandlerResult {
        Assert.hasText(resultJson, "resultJson must not be blank");
    }

    public static InvestmentJobHandlerResult completed(String resultJson) {
        return new InvestmentJobHandlerResult(resultJson, null);
    }

    public static InvestmentJobHandlerResult deferred(Instant nextAvailableAt) {
        Assert.notNull(nextAvailableAt, "nextAvailableAt must not be null");
        return new InvestmentJobHandlerResult("{}", nextAvailableAt);
    }

    public boolean deferred() {
        return nextAvailableAt != null;
    }
}
