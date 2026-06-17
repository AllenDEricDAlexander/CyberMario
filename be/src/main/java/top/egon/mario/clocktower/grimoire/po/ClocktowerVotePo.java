package top.egon.mario.clocktower.grimoire.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.common.entity.BaseAuditablePo;

@Getter
@Setter
@Entity
@Table(name = "clocktower_vote")
public class ClocktowerVotePo extends BaseAuditablePo {

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "nomination_id", nullable = false)
    private Long nominationId;

    @Column(name = "voter_seat_id", nullable = false)
    private Long voterSeatId;

    @Column(name = "vote_value", nullable = false)
    private boolean voteValue;

    @Column(name = "used_dead_vote", nullable = false)
    private boolean usedDeadVote;

    @Column(name = "event_id")
    private Long eventId;
}
