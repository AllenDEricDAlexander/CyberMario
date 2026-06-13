package top.egon.mario.rbac.converter.jpa;

import jakarta.persistence.Converter;
import top.egon.mario.rbac.po.enums.ButtonApiRelationType;

/**
 * Persists button-API relation type as an integer code.
 */
@Converter(autoApply = false)
public class ButtonApiRelationTypeConverter extends AbstractCodedEnumConverter<ButtonApiRelationType> {

    public ButtonApiRelationTypeConverter() {
        super(ButtonApiRelationType.class);
    }

}
