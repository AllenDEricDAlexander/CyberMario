package top.egon.mario.clocktower.game.po;

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
@Table(name = "clocktower_room_profile")
public class ClocktowerRoomProfilePo extends BaseAuditablePo {

    @Column(name = "room_id", nullable = false, unique = true)
    private Long roomId;

    @Column(name = "script_code", nullable = false, length = 64)
    private String scriptCode;

    @Column(name = "storyteller_user_id")
    private Long storytellerUserId;

    @Column(name = "player_count", nullable = false)
    private int playerCount;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "current_game_id")
    private Long currentGameId;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
