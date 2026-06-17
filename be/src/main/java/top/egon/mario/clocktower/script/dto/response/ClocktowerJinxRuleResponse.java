package top.egon.mario.clocktower.script.dto.response;

import top.egon.mario.clocktower.script.po.ClocktowerJinxRulePo;

public record ClocktowerJinxRuleResponse(
        String roleACode,
        String roleBCode,
        String scope,
        String severity,
        String effectType,
        String ruleText,
        String sourceUrl
) {

    public static ClocktowerJinxRuleResponse from(ClocktowerJinxRulePo rule) {
        return new ClocktowerJinxRuleResponse(rule.getRoleACode(), rule.getRoleBCode(), rule.getScope(),
                rule.getSeverity(), rule.getEffectType(), rule.getRuleText(), rule.getSourceUrl());
    }
}
