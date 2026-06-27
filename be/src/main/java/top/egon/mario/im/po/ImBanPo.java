package top.egon.mario.im.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.common.entity.BaseAuditablePo;
import top.egon.mario.im.po.enums.ImGovernanceStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "im_ban")
public class ImBanPo extends BaseAuditablePo {

    @Enumerated(EnumType.STRING)
    @Column(name = "surface_type", nullable = false, length = 32)
    private ImSurfaceType surfaceType;

    @Column(name = "surface_id", nullable = false)
    private Long surfaceId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ImGovernanceStatus status = ImGovernanceStatus.ACTIVE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
