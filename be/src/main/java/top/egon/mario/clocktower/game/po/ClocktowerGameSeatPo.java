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
@Table(name = "clocktower_game_seat")
public class ClocktowerGameSeatPo extends BaseAuditablePo {

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "room_seat_id")
    private Long roomSeatId;

    @Column(name = "seat_no", nullable = false)
    private int seatNo;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "role_code", length = 64)
    private String roleCode;

    @Column(name = "role_type", length = 32)
    private String roleType;

    @Column(name = "alignment", length = 32)
    private String alignment;

    @Column(name = "life_status", nullable = false, length = 32)
    private String lifeStatus = "ALIVE";

    @Column(name = "public_life_status", nullable = false, length = 32)
    private String publicLifeStatus = "ALIVE";

    @Column(name = "has_dead_vote", nullable = false)
    private boolean hasDeadVote = true;

    @Column(name = "is_traveler", nullable = false)
    private boolean traveler;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
