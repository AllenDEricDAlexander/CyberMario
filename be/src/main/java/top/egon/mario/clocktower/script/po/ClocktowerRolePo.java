package top.egon.mario.clocktower.script.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.common.entity.BaseAuditablePo;

@Getter
@Setter
@Entity
@Table(name = "clocktower_role")
public class ClocktowerRolePo extends BaseAuditablePo {

    @Enumerated(EnumType.STRING)
    @Column(name = "script_code", nullable = false, length = 64)
    private ClocktowerScriptCode scriptCode;

    @Column(name = "role_code", nullable = false, unique = true, length = 64)
    private String roleCode;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", nullable = false, length = 32)
    private ClocktowerRoleType roleType;

    @Column(name = "alignment", nullable = false, length = 32)
    private String alignment;

    @Column(name = "ability_text", nullable = false)
    private String abilityText;

    @Column(name = "first_night", nullable = false)
    private boolean firstNight;

    @Column(name = "other_night", nullable = false)
    private boolean otherNight;

    @Column(name = "setup_modifier", nullable = false)
    private boolean setupModifier;

    @Column(name = "complexity", nullable = false)
    private int complexity = 1;

    @Column(name = "first_night_order")
    private Integer firstNightOrder;

    @Column(name = "other_night_order")
    private Integer otherNightOrder;

    @Column(name = "first_night_reminder")
    private String firstNightReminder;

    @Column(name = "other_night_reminder")
    private String otherNightReminder;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "source_url", length = 512)
    private String sourceUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
