package top.egon.mario.investment.agent.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.investment.agent.model.InvestmentAgentAction;
import top.egon.mario.investment.agent.model.InvestmentAgentExecutionStatus;
import top.egon.mario.investment.common.model.OrderType;

import java.math.BigDecimal;
import java.time.Instant;

/** Immutable validated proposal; only execution state and its one intent link may change. */
@Getter
@Setter
@Entity
@Table(name = "investment_agent_decision", uniqueConstraints = {
        @UniqueConstraint(name = "uk_investment_agent_decision_intent", columnNames = "intent_id"),
        @UniqueConstraint(name = "uk_investment_agent_decision_execution",
                columnNames = "execution_idempotency_key")
})
public class InvestmentAgentDecisionPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, updatable = false)
    private Long runId;

    @Column(name = "instrument_id", updatable = false)
    private Long instrumentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 32, updatable = false)
    private InvestmentAgentAction action;

    @Column(name = "confidence", nullable = false, precision = 24, scale = 12, updatable = false)
    private BigDecimal confidence;

    @Column(name = "horizon", nullable = false, length = 64, updatable = false)
    private String horizon;

    @Column(name = "thesis", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String thesis;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risks_json", nullable = false, columnDefinition = "jsonb", updatable = false)
    private String risksJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "invalidation_json", nullable = false, columnDefinition = "jsonb", updatable = false)
    private String invalidationJson;

    @Column(name = "requested_quantity", precision = 38, scale = 18, updatable = false)
    private BigDecimal requestedQuantity;

    @Column(name = "requested_notional", precision = 38, scale = 18, updatable = false)
    private BigDecimal requestedNotional;

    @Column(name = "requested_leverage", precision = 24, scale = 12, updatable = false)
    private BigDecimal requestedLeverage;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", length = 16, updatable = false)
    private OrderType orderType;

    @Column(name = "limit_price", precision = 38, scale = 18, updatable = false)
    private BigDecimal limitPrice;

    @Column(name = "intent_id")
    private Long intentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_status", nullable = false, length = 32)
    private InvestmentAgentExecutionStatus executionStatus;

    @Column(name = "execution_idempotency_key", length = 128, updatable = false)
    private String executionIdempotencyKey;

    @Column(name = "data_as_of", nullable = false, updatable = false)
    private Instant dataAsOf;

    @Column(name = "expires_at", updatable = false)
    private Instant expiresAt;

    @Column(name = "status", nullable = false, length = 32, updatable = false)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
