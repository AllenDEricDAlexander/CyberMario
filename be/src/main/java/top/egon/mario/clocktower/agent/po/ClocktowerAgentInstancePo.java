package top.egon.mario.clocktower.agent.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentAutoMode;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentStatus;
import top.egon.mario.common.entity.BaseAuditablePo;

@Getter
@Setter
@Entity
@Table(name = "clocktower_agent_instance")
public class ClocktowerAgentInstancePo extends BaseAuditablePo {

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "game_id")
    private Long gameId;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "room_seat_id")
    private Long roomSeatId;

    @Column(name = "game_seat_id")
    private Long gameSeatId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = ClocktowerAgentStatus.ACTIVE;

    @Column(name = "auto_mode", nullable = false, length = 32)
    private String autoMode = ClocktowerAgentAutoMode.FULL_AUTO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
