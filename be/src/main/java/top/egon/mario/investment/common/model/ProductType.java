package top.egon.mario.investment.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ProductType implements InvestmentWireEnum {
    USDT_FUTURES;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ProductType fromWireValue(Object input) {
        return InvestmentWireEnum.fromWireValue(ProductType.class, input);
    }
}
