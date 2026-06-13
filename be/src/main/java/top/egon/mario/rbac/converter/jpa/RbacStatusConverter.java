package top.egon.mario.rbac.converter.jpa;

import jakarta.persistence.Converter;
import top.egon.mario.rbac.po.enums.RbacStatus;

/**
 * Persists RBAC status as an integer code.
 */
@Converter(autoApply = false)
public class RbacStatusConverter extends AbstractCodedEnumConverter<RbacStatus> {

    public RbacStatusConverter() {
        super(RbacStatus.class);
    }

}
