package top.egon.mario.clocktower.grimoire.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.common.entity.BaseAuditablePo;

@Getter
@Setter
@Entity
@Table(name = "clocktower_status_marker")
public class ClocktowerStatusMarkerPo extends BaseAuditablePo {

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "seat_id")
    private Long seatId;

    @Column(name = "marker_code", nullable = false, length = 64)
    private String markerCode;

    @Column(name = "marker_name", nullable = false, length = 128)
    private String markerName;

    @Column(name = "marker_source", length = 64)
    private String markerSource;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "expires_phase", length = 32)
    private String expiresPhase;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson = "{}";
}
