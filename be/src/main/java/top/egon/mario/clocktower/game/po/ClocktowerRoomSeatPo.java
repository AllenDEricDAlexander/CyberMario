package top.egon.mario.clocktower.game.po;

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
@Table(name = "clocktower_room_seat")
public class ClocktowerRoomSeatPo extends BaseAuditablePo {

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "room_member_id")
    private Long roomMemberId;

    @Column(name = "seat_no", nullable = false)
    private int seatNo;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "role_code", length = 64)
    private String roleCode;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "OPEN";

    @Column(name = "is_traveler", nullable = false)
    private boolean traveler;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
