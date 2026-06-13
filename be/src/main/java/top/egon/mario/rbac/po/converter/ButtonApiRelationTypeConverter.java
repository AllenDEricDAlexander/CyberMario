package top.egon.mario.rbac.po.converter;

import jakarta.persistence.Converter;
import top.egon.mario.rbac.po.ButtonApiRelationType;

/**
 * Persists button-API relation type as an integer code.
 */
@Converter(autoApply = false)
public class ButtonApiRelationTypeConverter extends AbstractCodedEnumConverter<ButtonApiRelationType> {

    public ButtonApiRelationTypeConverter() {
        super(ButtonApiRelationType.class);
    }

}
