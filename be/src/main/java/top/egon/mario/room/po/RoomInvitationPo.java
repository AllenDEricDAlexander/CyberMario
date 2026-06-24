package top.egon.mario.room.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.common.entity.BaseAuditablePo;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "room_invitation")
public class RoomInvitationPo extends BaseAuditablePo {

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "inviter_user_id", nullable = false)
    private Long inviterUserId;

    @Column(name = "invitee_user_id")
    private Long inviteeUserId;

    @Column(name = "invitation_code", nullable = false, unique = true, length = 64)
    private String invitationCode;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "PENDING";

    @Column(name = "active_status")
    private Boolean activeStatus = true;

    @Column(name = "target_seat_no")
    private Integer targetSeatNo;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
