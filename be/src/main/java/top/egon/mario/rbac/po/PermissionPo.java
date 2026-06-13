package top.egon.mario.rbac.po;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.common.entity.BaseAuditablePo;
import top.egon.mario.rbac.converter.jpa.PermissionStatusConverter;
import top.egon.mario.rbac.converter.jpa.PermissionTypeConverter;
import top.egon.mario.rbac.po.enums.PermissionStatus;
import top.egon.mario.rbac.po.enums.PermissionType;

import java.time.Instant;

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

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

}
