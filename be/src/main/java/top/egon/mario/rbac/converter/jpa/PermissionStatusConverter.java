package top.egon.mario.rbac.converter.jpa;

import jakarta.persistence.Converter;
import top.egon.mario.rbac.po.enums.PermissionStatus;

/**
 * Persists permission status as an integer code.
 */
@Converter(autoApply = false)
public class PermissionStatusConverter extends AbstractCodedEnumConverter<PermissionStatus> {

    public PermissionStatusConverter() {
        super(PermissionStatus.class);
    }

}
