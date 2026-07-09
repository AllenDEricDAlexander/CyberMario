package top.egon.mario.clocktower.agent.decision.po;

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
@Table(name = "clocktower_agent_decision")
public class ClocktowerAgentDecisionPo extends BaseAuditablePo {

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "agent_instance_id", nullable = false)
    private Long agentInstanceId;

    @Column(name = "game_seat_id", nullable = false)
    private Long gameSeatId;

    @Column(name = "trigger_task_id")
    private Long triggerTaskId;

    @Column(name = "phase", nullable = false, length = 32)
    private String phase;

    @Column(name = "day_no", nullable = false)
    private int dayNo;

    @Column(name = "night_no", nullable = false)
    private int nightNo;

    @Column(name = "decision_type", nullable = false, length = 64)
    private String decisionType;

    @Column(name = "policy_type", nullable = false, length = 32)
    private String policyType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "legal_intents_json", nullable = false, columnDefinition = "jsonb")
    private String legalIntentsJson = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_intent_json", nullable = false, columnDefinition = "jsonb")
    private String selectedIntentJson = "{}";

    @Column(name = "reasoning_summary")
    private String reasoningSummary;

    @Column(name = "model_provider", length = 64)
    private String modelProvider;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @Column(name = "prompt_hash", length = 128)
    private String promptHash;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACCEPTED";

    @Column(name = "error_message")
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
