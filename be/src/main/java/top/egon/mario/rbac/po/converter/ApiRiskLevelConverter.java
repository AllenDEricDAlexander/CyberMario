package top.egon.mario.rbac.po.converter;

import jakarta.persistence.Converter;
import top.egon.mario.rbac.po.ApiRiskLevel;

/**
 * Persists API risk level as an integer code.
 */
@Converter(autoApply = false)
public class ApiRiskLevelConverter extends AbstractCodedEnumConverter<ApiRiskLevel> {

    public ApiRiskLevelConverter() {
        super(ApiRiskLevel.class);
    }

}
