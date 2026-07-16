package top.egon.mario.investment.agent.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.common.entity.BaseAuditablePo;
import top.egon.mario.investment.agent.model.InvestmentAgentRunType;
import top.egon.mario.investment.common.model.InvestmentRunStatus;

import java.time.Instant;

/** Owner-scoped Investment domain run linked one-to-one with the generic Agent audit. */
@Getter
@Setter
@Entity
@Table(name = "investment_agent_run", uniqueConstraints = {
        @UniqueConstraint(name = "uk_investment_agent_run_idempotency", columnNames = "idempotency_key"),
        @UniqueConstraint(name = "uk_investment_agent_run_generic_audit", columnNames = "generic_agent_run_audit_id")
})
public class InvestmentAgentRunPo extends BaseAuditablePo {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private Long workspaceId;

    @Column(name = "account_id", updatable = false)
    private Long accountId;

    @Column(name = "agent_preset_code", nullable = false, length = 128, updatable = false)
    private String agentPresetCode;

    @Column(name = "generic_agent_run_audit_id", nullable = false, updatable = false)
    private Long genericAgentRunAuditId;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false, length = 32, updatable = false)
    private InvestmentAgentRunType runType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private InvestmentRunStatus status;

    @Column(name = "data_as_of", nullable = false, updatable = false)
    private Instant dataAsOf;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot_json", nullable = false, columnDefinition = "jsonb", updatable = false)
    private String inputSnapshotJson;

    @Column(name = "report_id")
    private Long reportId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_code", length = 256)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "idempotency_key", nullable = false, length = 128, updatable = false)
    private String idempotencyKey;
}
