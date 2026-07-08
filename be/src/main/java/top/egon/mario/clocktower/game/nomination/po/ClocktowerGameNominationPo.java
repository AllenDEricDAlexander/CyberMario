package top.egon.mario.clocktower.game.nomination.po;

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
@Table(name = "clocktower_game_nomination")
public class ClocktowerGameNominationPo extends BaseAuditablePo {

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "open_game_id")
    private Long openGameId;

    @Column(name = "day_no", nullable = false)
    private int dayNo;

    @Column(name = "nominator_game_seat_id", nullable = false)
    private Long nominatorGameSeatId;

    @Column(name = "nominee_game_seat_id", nullable = false)
    private Long nomineeGameSeatId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "vote_count", nullable = false)
    private int voteCount;

    @Column(name = "required_votes", nullable = false)
    private int requiredVotes;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
