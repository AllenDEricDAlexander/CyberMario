package top.egon.mario.clocktower.ruling.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingReason;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingStatus;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingType;
import top.egon.mario.clocktower.common.enums.ClocktowerVisibility;
import top.egon.mario.common.entity.BaseAuditablePo;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "clocktower_ruling")
public class ClocktowerRulingPo extends BaseAuditablePo {

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ruling_type", nullable = false, length = 64)
    private ClocktowerRulingType rulingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ClocktowerRulingStatus status = ClocktowerRulingStatus.APPLIED;

    @Column(name = "target_seat_id")
    private Long targetSeatId;

    @Column(name = "nomination_id")
    private Long nominationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_phase", length = 32)
    private ClocktowerPhase targetPhase;

    @Column(name = "public_life_status", length = 32)
    private String publicLifeStatus;

    @Column(name = "winner", length = 32)
    private String winner;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 64)
    private ClocktowerRulingReason reason;

    @Column(name = "note", nullable = false)
    private String note;

    @Column(name = "public_note")
    private String publicNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 32)
    private ClocktowerVisibility visibility = ClocktowerVisibility.PUBLIC;

    @Column(name = "undo_of_ruling_id")
    private Long undoOfRulingId;

    @Column(name = "event_ids_json", nullable = false)
    private String eventIdsJson = "[]";

    @Column(name = "snapshot_json", nullable = false)
    private String snapshotJson = "{}";

    @Column(name = "revoked_by")
    private Long revokedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}
