package top.egon.mario.rbac.po.converter;

import jakarta.persistence.Converter;
import top.egon.mario.rbac.po.ApiMatcherType;

/**
 * Persists API matcher type as an integer code.
 */
@Converter(autoApply = false)
public class ApiMatcherTypeConverter extends AbstractCodedEnumConverter<ApiMatcherType> {

    public ApiMatcherTypeConverter() {
        super(ApiMatcherType.class);
    }

}
