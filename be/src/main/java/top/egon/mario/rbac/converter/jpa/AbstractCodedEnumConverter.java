package top.egon.mario.rbac.converter.jpa;

import jakarta.persistence.AttributeConverter;
import top.egon.mario.rbac.common.CodedEnum;

/**
 * Base JPA converter that persists coded enums as integer codes.
 */
public abstract class AbstractCodedEnumConverter<E extends Enum<E> & CodedEnum> implements AttributeConverter<E, Integer> {

    private final Class<E> enumType;

    protected AbstractCodedEnumConverter(Class<E> enumType) {
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
