package top.egon.mario.investment.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum OrderType implements InvestmentWireEnum {
    MARKET,
    LIMIT;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static OrderType fromWireValue(Object input) {
        return InvestmentWireEnum.fromWireValue(OrderType.class, input);
    }
}
