package top.egon.mario.clocktower.board.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.common.entity.BaseAuditablePo;

@Getter
@Setter
@Entity
@Table(name = "clocktower_board_config")
public class ClocktowerBoardConfigPo extends BaseAuditablePo {

    @Column(name = "board_code", nullable = false, unique = true, length = 64)
    private String boardCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "script_code", nullable = false, length = 64)
    private ClocktowerScriptCode scriptCode;

    @Column(name = "player_count", nullable = false)
    private int playerCount;

    @Column(name = "difficulty", nullable = false)
    private int difficulty;

    @Column(name = "chaos", nullable = false)
    private int chaos;

    @Column(name = "evil_pressure", nullable = false)
    private int evilPressure;

    @Column(name = "newbie_friendly", nullable = false)
    private boolean newbieFriendly;

    @Column(name = "seed", length = 128)
    private String seed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_json", nullable = false, columnDefinition = "jsonb")
    private String validationJson = "{}";
}
