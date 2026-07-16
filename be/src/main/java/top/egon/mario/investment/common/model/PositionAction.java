package top.egon.mario.investment.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum PositionAction implements InvestmentWireEnum {
    OPEN,
    REDUCE,
    CLOSE;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static PositionAction fromWireValue(Object input) {
        return InvestmentWireEnum.fromWireValue(PositionAction.class, input);
    }
}
