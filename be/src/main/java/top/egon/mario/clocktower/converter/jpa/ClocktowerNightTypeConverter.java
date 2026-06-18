package top.egon.mario.clocktower.converter.jpa;

import jakarta.persistence.Converter;
import top.egon.mario.clocktower.common.enums.ClocktowerNightType;

/**
 * Persists Clocktower night type as an integer code.
 */
@Converter(autoApply = false)
public class ClocktowerNightTypeConverter extends AbstractClocktowerCodedEnumConverter<ClocktowerNightType> {

    public ClocktowerNightTypeConverter() {
        super(ClocktowerNightType.class);
    }
}
