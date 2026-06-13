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
import top.egon.mario.rbac.dto.RoleResponse;

/**
 * Role that groups permissions and can inherit other roles.
 */
@Getter
@Setter
@Entity
@AutoMapper(target = RoleResponse.class)
@Table(name = "sys_role", uniqueConstraints = {
        @UniqueConstraint(name = "uk_role_code_deleted", columnNames = {"role_code", "deleted"})
})
public class RolePo extends BaseAuditablePo {

    @Column(name = "role_code", nullable = false, length = 64)
    private String roleCode;

    @Column(name = "role_name", nullable = false, length = 64)
    private String roleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RbacStatus status = RbacStatus.ENABLED;

    @Column(name = "sort_no", nullable = false)
    private int sortNo;

    @Column(name = "built_in", nullable = false)
    private boolean builtIn;

    @Column(name = "description", length = 255)
    private String description;

}
