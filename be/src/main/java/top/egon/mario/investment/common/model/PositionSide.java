package top.egon.mario.investment.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum PositionSide implements InvestmentWireEnum {
    LONG,
    SHORT;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static PositionSide fromWireValue(Object input) {
        return InvestmentWireEnum.fromWireValue(PositionSide.class, input);
    }
}
