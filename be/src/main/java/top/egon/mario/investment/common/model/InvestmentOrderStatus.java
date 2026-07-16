package top.egon.mario.investment.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum InvestmentOrderStatus implements InvestmentWireEnum {
    PENDING,
    FILLED,
    CANCELLED,
    EXPIRED,
    REJECTED;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static InvestmentOrderStatus fromWireValue(Object input) {
        return InvestmentWireEnum.fromWireValue(InvestmentOrderStatus.class, input);
    }
}
