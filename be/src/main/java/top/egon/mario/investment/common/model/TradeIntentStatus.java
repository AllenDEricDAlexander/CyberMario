package top.egon.mario.investment.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum TradeIntentStatus implements InvestmentWireEnum {
    RECEIVED,
    RISK_REJECTED,
    ACCEPTED,
    EXPIRED,
    FAILED;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static TradeIntentStatus fromWireValue(Object input) {
        return InvestmentWireEnum.fromWireValue(TradeIntentStatus.class, input);
    }
}
