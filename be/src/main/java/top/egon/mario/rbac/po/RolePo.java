package top.egon.mario.rbac.po;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.common.entity.BaseAuditablePo;
import top.egon.mario.rbac.converter.jpa.RbacStatusConverter;
import top.egon.mario.rbac.po.enums.RbacStatus;

import java.time.Instant;

/**
 * Role that groups permissions and can inherit other roles.
 */
@Getter
@Setter
@Entity
@Table(name = "sys_role", uniqueConstraints = {
        @UniqueConstraint(name = "uk_role_code_deleted", columnNames = {"role_code", "deleted"})
})
public class RolePo extends BaseAuditablePo {

    @Column(name = "role_code", nullable = false, length = 64)
    private String roleCode;

    @Column(name = "role_name", nullable = false, length = 64)
    private String roleName;

    @Convert(converter = RbacStatusConverter.class)
    @Column(name = "status", nullable = false)
    private RbacStatus status = RbacStatus.ENABLED;

    @Column(name = "sort_no", nullable = false)
    private int sortNo;

    @Column(name = "built_in", nullable = false)
    private boolean builtIn;

    @Column(name = "permission_version", nullable = false)
    private long permissionVersion;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "managed", nullable = false)
    private boolean managed;

    @Column(name = "owner_app", length = 64)
    private String ownerApp;

    @Column(name = "source_type", length = 32)
    private String sourceType;

    @Column(name = "source_key", length = 256)
    private String sourceKey;

    @Column(name = "sync_hash", length = 64)
    private String syncHash;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

}
