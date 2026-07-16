package top.egon.mario.investment.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum MarginMode implements InvestmentWireEnum {
    ISOLATED;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static MarginMode fromWireValue(Object input) {
        return InvestmentWireEnum.fromWireValue(MarginMode.class, input);
    }
}
