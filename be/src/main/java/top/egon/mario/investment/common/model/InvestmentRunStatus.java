package top.egon.mario.investment.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum InvestmentRunStatus implements InvestmentWireEnum {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static InvestmentRunStatus fromWireValue(Object input) {
        return InvestmentWireEnum.fromWireValue(InvestmentRunStatus.class, input);
    }
}
