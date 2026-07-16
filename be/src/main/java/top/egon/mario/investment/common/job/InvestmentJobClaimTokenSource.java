package top.egon.mario.investment.common.job;

/**
 * Supplies fencing tokens and provides a deterministic replacement seam for job tests.
 */
@FunctionalInterface
public interface InvestmentJobClaimTokenSource {

    String nextToken();
}
