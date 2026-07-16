package top.egon.mario.investment.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum PriceType implements InvestmentWireEnum {
    MARKET,
    MARK,
    INDEX,
    NONE;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static PriceType fromWireValue(Object input) {
        return InvestmentWireEnum.fromWireValue(PriceType.class, input);
    }
}
