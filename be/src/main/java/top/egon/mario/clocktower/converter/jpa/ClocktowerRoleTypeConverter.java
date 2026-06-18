package top.egon.mario.clocktower.converter.jpa;

import jakarta.persistence.Converter;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;

/**
 * Persists Clocktower role type as an integer code.
 */
@Converter(autoApply = false)
public class ClocktowerRoleTypeConverter extends AbstractClocktowerCodedEnumConverter<ClocktowerRoleType> {

    public ClocktowerRoleTypeConverter() {
        super(ClocktowerRoleType.class);
    }
}
