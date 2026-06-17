package top.egon.mario.clocktower.board.dto.response;

import top.egon.mario.clocktower.engine.RuleViolationDecision;

public record ClocktowerRuleViolationResponse(String code, String message, String severity) {

    public static ClocktowerRuleViolationResponse from(RuleViolationDecision decision) {
        return new ClocktowerRuleViolationResponse(decision.code(), decision.message(), decision.severity());
    }
}
