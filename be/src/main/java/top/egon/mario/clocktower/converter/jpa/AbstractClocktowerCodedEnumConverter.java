package top.egon.mario.clocktower.converter.jpa;

import jakarta.persistence.AttributeConverter;
import top.egon.mario.common.enums.CodedEnum;

/**
 * Base JPA converter that persists Clocktower coded enums as integer codes.
 */
public abstract class AbstractClocktowerCodedEnumConverter<E extends Enum<E> & CodedEnum>
        implements AttributeConverter<E, Integer> {

    private final Class<E> enumType;

    protected AbstractClocktowerCodedEnumConverter(Class<E> enumType) {
        this.enumType = enumType;
    }

    @Override
    public Integer convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public E convertToEntityAttribute(Integer dbData) {
        return CodedEnum.fromCode(enumType, dbData);
    }
}
