package top.egon.mario.rbac.converter.jpa;

import jakarta.persistence.Converter;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;

/**
 * Persists API risk level as an integer code.
 */
@Converter(autoApply = false)
public class ApiRiskLevelConverter extends AbstractCodedEnumConverter<ApiRiskLevel> {

    public ApiRiskLevelConverter() {
        super(ApiRiskLevel.class);
    }

}
