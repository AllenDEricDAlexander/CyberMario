package top.egon.mario.investment.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum PositionMode implements InvestmentWireEnum {
    ONE_WAY;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static PositionMode fromWireValue(Object input) {
        return InvestmentWireEnum.fromWireValue(PositionMode.class, input);
    }
}
