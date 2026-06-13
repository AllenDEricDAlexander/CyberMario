package top.egon.mario.rbac.po;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.rbac.dto.PermissionResponse;

/**
 * Unified permission metadata shared by menu, button and API resources.
 */
@Getter
@Setter
@Entity
@AutoMapper(target = PermissionResponse.class)
@Table(name = "sys_permission", uniqueConstraints = {
        @UniqueConstraint(name = "uk_permission_code_deleted", columnNames = {"perm_code", "deleted"})
})
public class PermissionPo extends BaseAuditablePo {

    @Column(name = "perm_code", nullable = false, length = 128)
    private String permCode;

    @Column(name = "perm_name", nullable = false, length = 128)
    private String permName;

    @Enumerated(EnumType.STRING)
    @Column(name = "perm_type", nullable = false, length = 20)
    private PermissionType permType;

    @Column(name = "parent_id")
    private Long parentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PermissionStatus status = PermissionStatus.ENABLED;

    @Column(name = "sort_no", nullable = false)
    private int sortNo;

    @Column(name = "description", length = 255)
    private String description;

}
