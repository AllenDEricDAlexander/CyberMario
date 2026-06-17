package top.egon.mario.clocktower.room.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerRoomStatus;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.common.entity.BaseAuditablePo;

@Getter
@Setter
@Entity
@Table(name = "clocktower_room")
public class ClocktowerRoomPo extends BaseAuditablePo {

    @Column(name = "room_code", nullable = false, unique = true, length = 32)
    private String roomCode;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "script_code", nullable = false, length = 64)
    private ClocktowerScriptCode scriptCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ClocktowerRoomStatus status = ClocktowerRoomStatus.LOBBY;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", nullable = false, length = 32)
    private ClocktowerPhase phase = ClocktowerPhase.LOBBY;

    @Column(name = "player_count", nullable = false)
    private int playerCount;

    @Column(name = "storyteller_user_id")
    private Long storytellerUserId;

    @Column(name = "storyteller_mode", nullable = false, length = 32)
    private String storytellerMode = "HUMAN";

    @Column(name = "allow_spectators", nullable = false)
    private boolean allowSpectators;

    @Column(name = "allow_private_chat", nullable = false)
    private boolean allowPrivateChat = true;

    @Column(name = "current_day_no", nullable = false)
    private int currentDayNo;

    @Column(name = "current_night_no", nullable = false)
    private int currentNightNo;
}
