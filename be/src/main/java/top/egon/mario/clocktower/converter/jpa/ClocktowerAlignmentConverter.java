package top.egon.mario.clocktower.converter.jpa;

import jakarta.persistence.Converter;
import top.egon.mario.clocktower.common.enums.ClocktowerAlignment;

/**
 * Persists Clocktower alignment as an integer code.
 */
@Converter(autoApply = false)
public class ClocktowerAlignmentConverter extends AbstractClocktowerCodedEnumConverter<ClocktowerAlignment> {

    public ClocktowerAlignmentConverter() {
        super(ClocktowerAlignment.class);
    }
}
