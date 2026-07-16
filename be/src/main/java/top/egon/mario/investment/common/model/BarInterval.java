package top.egon.mario.investment.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum BarInterval implements InvestmentWireEnum {
    M1,
    M5,
    M15,
    M30,
    H1,
    H4,
    D1,
    NONE;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static BarInterval fromWireValue(Object input) {
        return InvestmentWireEnum.fromWireValue(BarInterval.class, input);
    }
}
