package top.egon.mario.investment.common.job;

/**
 * Result of a fenced retry transition.
 */
public enum InvestmentJobTransition {
    RETRY_SCHEDULED,
    TERMINAL_FAILED,
    REJECTED
}
