package top.egon.mario.investment.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ContractType implements InvestmentWireEnum {
    PERPETUAL;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ContractType fromWireValue(Object input) {
        return InvestmentWireEnum.fromWireValue(ContractType.class, input);
    }
}
