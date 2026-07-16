package top.egon.mario.investment.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum InvestmentJobStatus implements InvestmentWireEnum {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static InvestmentJobStatus fromWireValue(Object input) {
        return InvestmentWireEnum.fromWireValue(InvestmentJobStatus.class, input);
    }
}
