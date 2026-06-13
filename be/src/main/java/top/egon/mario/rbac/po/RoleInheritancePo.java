package top.egon.mario.rbac.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * RBAC1 inheritance edge where roleId inherits inheritedRoleId.
 */
@Getter
@Setter
@Entity
@Table(name = "sys_role_inheritance", uniqueConstraints = {
        @UniqueConstraint(name = "uk_role_inheritance", columnNames = {"role_id", "inherited_role_id"})
})
public class RoleInheritancePo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "inherited_role_id", nullable = false)
    private Long inheritedRoleId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private Long createdBy;

}
