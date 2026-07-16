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
import top.egon.mario.im.po.enums.ImFriendshipStatus;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "im_friendship")
public class ImFriendshipPo extends BaseAuditablePo {

    @Column(name = "user_lo_id", nullable = false)
    private Long userLoId;

    @Column(name = "user_hi_id", nullable = false)
    private Long userHiId;

    @Column(name = "requester_user_id", nullable = false)
    private Long requesterUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ImFriendshipStatus status = ImFriendshipStatus.PENDING;

    @Column(name = "request_message", nullable = false, length = 512)
    private String requestMessage = "";

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason", length = 512)
    private String decisionReason;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
