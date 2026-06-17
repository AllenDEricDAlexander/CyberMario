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
@Table(name = "clocktower_nomination")
public class ClocktowerNominationPo extends BaseAuditablePo {

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "day_no", nullable = false)
    private int dayNo;

    @Column(name = "nominator_seat_id", nullable = false)
    private Long nominatorSeatId;

    @Column(name = "nominee_seat_id", nullable = false)
    private Long nomineeSeatId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "vote_count", nullable = false)
    private int voteCount;

    @Column(name = "executed", nullable = false)
    private boolean executed;
}
