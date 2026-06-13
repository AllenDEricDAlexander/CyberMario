package top.egon.mario.rbac.po;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.rbac.converter.jpa.PermissionStatusConverter;
import top.egon.mario.rbac.converter.jpa.PermissionTypeConverter;
import top.egon.mario.rbac.po.enums.PermissionStatus;
import top.egon.mario.rbac.po.enums.PermissionType;

/**
 * Unified permission metadata shared by menu, button and API resources.
 */
@Getter
@Setter
@Entity
@Table(name = "sys_permission", uniqueConstraints = {
        @UniqueConstraint(name = "uk_permission_code_deleted", columnNames = {"perm_code", "deleted"})
})
public class PermissionPo extends BaseAuditablePo {

    @Column(name = "perm_code", nullable = false, length = 128)
    private String permCode;

    @Column(name = "perm_name", nullable = false, length = 128)
    private String permName;

    @Convert(converter = PermissionTypeConverter.class)
    @Column(name = "perm_type", nullable = false)
    private PermissionType permType;

    @Column(name = "parent_id")
    private Long parentId;

    @Convert(converter = PermissionStatusConverter.class)
    @Column(name = "status", nullable = false)
    private PermissionStatus status = PermissionStatus.ENABLED;

    @Column(name = "sort_no", nullable = false)
    private int sortNo;

    @Column(name = "description", length = 255)
    private String description;

}
