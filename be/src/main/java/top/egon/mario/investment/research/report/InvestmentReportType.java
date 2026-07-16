package top.egon.mario.investment.research.report;

import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;

import java.util.Locale;

/**
 * Stable report taxonomy. A type may exist before its owning generator is installed.
 */
public enum InvestmentReportType {
    MARKET_OVERVIEW,
    INSTRUMENT_ANALYSIS,
    STRATEGY_ANALYSIS,
    BACKTEST_REPORT,
    PORTFOLIO_REPORT,
    AGENT_ANALYSIS;

    public static InvestmentReportType parse(String value) {
        try {
            return value == null ? null : valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvestmentException(InvestmentErrorCode.INVALID_REQUEST,
                    "Unsupported research report type: " + value, exception);
        }
    }
}
