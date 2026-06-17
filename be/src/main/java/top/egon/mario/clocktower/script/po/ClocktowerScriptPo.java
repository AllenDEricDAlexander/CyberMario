package top.egon.mario.clocktower.script.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.common.entity.BaseAuditablePo;

@Getter
@Setter
@Entity
@Table(name = "clocktower_script")
public class ClocktowerScriptPo extends BaseAuditablePo {

    @Enumerated(EnumType.STRING)
    @Column(name = "script_code", nullable = false, unique = true, length = 64)
    private ClocktowerScriptCode scriptCode;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "edition", nullable = false, length = 128)
    private String edition;

    @Column(name = "min_players", nullable = false)
    private int minPlayers;

    @Column(name = "max_players", nullable = false)
    private int maxPlayers;

    @Column(name = "role_count", nullable = false)
    private int roleCount;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "source_url", length = 512)
    private String sourceUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
