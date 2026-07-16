package top.egon.mario.investment.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum InvestmentJobType implements InvestmentWireEnum {
    CONTRACT_SYNC,
    POSITION_TIER_SYNC,
    BAR_BACKFILL,
    BAR_INCREMENTAL,
    QUOTE_REFRESH,
    FUNDING_RATE_BACKFILL,
    FUNDING_RATE_INCREMENTAL,
    DATA_QUALITY_CHECK,
    REPORT_BUILD,
    BACKTEST_RUN,
    PAPER_MATCH,
    PAPER_FUNDING_SETTLE,
    PAPER_MARGIN_CHECK,
    AGENT_ANALYSIS;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static InvestmentJobType fromWireValue(Object input) {
        return InvestmentWireEnum.fromWireValue(InvestmentJobType.class, input);
    }
}
