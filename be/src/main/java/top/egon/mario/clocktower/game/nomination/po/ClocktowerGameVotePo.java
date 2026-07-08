package top.egon.mario.clocktower.game.nomination.po;

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
@Table(name = "clocktower_game_vote")
public class ClocktowerGameVotePo extends BaseAuditablePo {

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "nomination_id", nullable = false)
    private Long nominationId;

    @Column(name = "voter_game_seat_id", nullable = false)
    private Long voterGameSeatId;

    @Column(name = "vote_value", nullable = false)
    private boolean voteValue;

    @Column(name = "used_dead_vote", nullable = false)
    private boolean usedDeadVote;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
