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
import top.egon.mario.im.po.enums.ImSurfaceInvitationStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "im_surface_invitation")
public class ImSurfaceInvitationPo extends BaseAuditablePo {

    @Enumerated(EnumType.STRING)
    @Column(name = "surface_type", nullable = false, length = 32)
    private ImSurfaceType surfaceType;

    @Column(name = "surface_id", nullable = false)
    private Long surfaceId;

    @Column(name = "inviter_user_id", nullable = false)
    private Long inviterUserId;

    @Column(name = "invitee_user_id", nullable = false)
    private Long inviteeUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ImSurfaceInvitationStatus status = ImSurfaceInvitationStatus.PENDING;

    @Column(name = "message", nullable = false, length = 512)
    private String message = "";

    @Column(name = "responded_at")
    private Instant respondedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
