package top.egon.mario.clocktower.agent.po;

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
@Table(name = "clocktower_agent_profile")
public class ClocktowerAgentProfilePo extends BaseAuditablePo {

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "display_name_template", nullable = false, length = 128)
    private String displayNameTemplate;

    @Column(name = "strategy_level", nullable = false, length = 32)
    private String strategyLevel = "NORMAL";

    @Column(name = "talkativeness", nullable = false)
    private int talkativeness = 50;

    @Column(name = "deception_level", nullable = false)
    private int deceptionLevel = 50;

    @Column(name = "aggression", nullable = false)
    private int aggression = 50;

    @Column(name = "risk_tolerance", nullable = false)
    private int riskTolerance = 50;

    @Column(name = "model_provider", length = 64)
    private String modelProvider;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
