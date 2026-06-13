package top.egon.mario.rbac.converter.jpa;

import jakarta.persistence.Converter;
import top.egon.mario.rbac.po.enums.PermissionType;

/**
 * Persists permission type as an integer code.
 */
@Converter(autoApply = false)
public class PermissionTypeConverter extends AbstractCodedEnumConverter<PermissionType> {

    public PermissionTypeConverter() {
        super(PermissionType.class);
    }

}
