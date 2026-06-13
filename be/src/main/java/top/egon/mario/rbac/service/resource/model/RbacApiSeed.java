package top.egon.mario.rbac.service.resource.model;

import top.egon.mario.rbac.po.enums.ApiMatcherType;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;

/**
 * API-specific declaration carried by an API resource seed.
 */
public record RbacApiSeed(
        String httpMethod,
        String urlPattern,
        ApiMatcherType matcherType,
        boolean publicFlag,
        ApiRiskLevel riskLevel
) {
}
