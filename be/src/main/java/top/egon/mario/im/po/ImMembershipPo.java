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
import top.egon.mario.im.po.enums.ImMembershipRole;
import top.egon.mario.im.po.enums.ImMembershipStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "im_membership")
public class ImMembershipPo extends BaseAuditablePo {

    @Enumerated(EnumType.STRING)
    @Column(name = "surface_type", nullable = false, length = 32)
    private ImSurfaceType surfaceType;

    @Column(name = "surface_id", nullable = false)
    private Long surfaceId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", nullable = false, length = 32)
    private ImMembershipRole memberRole = ImMembershipRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ImMembershipStatus status = ImMembershipStatus.ACTIVE;

    @Column(name = "muted_until")
    private Instant mutedUntil;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
