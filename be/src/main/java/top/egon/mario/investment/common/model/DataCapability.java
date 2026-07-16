package top.egon.mario.investment.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum DataCapability implements InvestmentWireEnum {
    CONTRACT_METADATA,
    MARKET_CANDLE,
    MARK_CANDLE,
    INDEX_CANDLE,
    LATEST_TICKER,
    FUNDING_RATE,
    POSITION_TIER,
    OPEN_INTEREST;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static DataCapability fromWireValue(Object input) {
        return InvestmentWireEnum.fromWireValue(DataCapability.class, input);
    }
}
