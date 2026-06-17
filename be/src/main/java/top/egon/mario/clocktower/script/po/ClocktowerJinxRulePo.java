package top.egon.mario.clocktower.script.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.common.entity.BaseAuditablePo;

@Getter
@Setter
@Entity
@Table(name = "clocktower_jinx_rule")
public class ClocktowerJinxRulePo extends BaseAuditablePo {

    @Column(name = "role_a_code", nullable = false, length = 64)
    private String roleACode;

    @Column(name = "role_b_code", nullable = false, length = 64)
    private String roleBCode;

    @Column(name = "scope", nullable = false, length = 32)
    private String scope;

    @Column(name = "severity", nullable = false, length = 32)
    private String severity;

    @Column(name = "effect_type", nullable = false, length = 64)
    private String effectType;

    @Column(name = "rule_text", nullable = false)
    private String ruleText;

    @Column(name = "source_url", length = 512)
    private String sourceUrl;
}
