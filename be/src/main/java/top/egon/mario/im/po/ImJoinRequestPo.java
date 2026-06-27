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
import top.egon.mario.im.po.enums.ImJoinRequestStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "im_join_request")
public class ImJoinRequestPo extends BaseAuditablePo {

    @Enumerated(EnumType.STRING)
    @Column(name = "surface_type", nullable = false, length = 32)
    private ImSurfaceType surfaceType;

    @Column(name = "surface_id", nullable = false)
    private Long surfaceId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ImJoinRequestStatus status = ImJoinRequestStatus.PENDING;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason", length = 512)
    private String decisionReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
