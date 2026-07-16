package top.egon.mario.investment.trading.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.common.entity.BaseAuditablePo;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "investment_trade_intent", uniqueConstraints = {
        @UniqueConstraint(name = "uk_investment_trade_intent_idempotency", columnNames = "idempotency_key")
})
public class InvestmentTradeIntentPo extends BaseAuditablePo {

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;
    @Column(name = "account_id", nullable = false)
    private Long accountId;
    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;
    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;
    @Column(name = "source_reference_id", length = 128)
    private String sourceReferenceId;
    @Column(name = "idempotency_key", nullable = false, length = 128, updatable = false)
    private String idempotencyKey;
    @Column(name = "position_action", nullable = false, length = 32)
    private String positionAction;
    @Column(name = "side", nullable = false, length = 16)
    private String side;
    @Column(name = "order_type", nullable = false, length = 16)
    private String orderType;
    @Column(name = "quantity", nullable = false, precision = 38, scale = 18)
    private BigDecimal quantity;
    @Column(name = "requested_notional", nullable = false, precision = 38, scale = 18)
    private BigDecimal requestedNotional;
    @Column(name = "leverage", nullable = false, precision = 24, scale = 12)
    private BigDecimal leverage;
    @Column(name = "limit_price", precision = 38, scale = 18)
    private BigDecimal limitPrice;
    @Column(name = "reduce_only", nullable = false)
    private boolean reduceOnly;
    @Column(name = "reason", length = 2000)
    private String reason;
    @Column(name = "data_as_of", nullable = false)
    private Instant dataAsOf;
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    @Column(name = "risk_checked_at")
    private Instant riskCheckedAt;
    @Column(name = "accepted_at")
    private Instant acceptedAt;
    @Column(name = "expires_at")
    private Instant expiresAt;
}
