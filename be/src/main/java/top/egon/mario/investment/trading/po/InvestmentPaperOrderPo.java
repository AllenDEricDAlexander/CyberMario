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
@Table(name = "investment_paper_order", uniqueConstraints = {
        @UniqueConstraint(name = "uk_investment_paper_order_client", columnNames = "client_order_id"),
        @UniqueConstraint(name = "uk_investment_paper_order_intent", columnNames = "intent_id")
})
public class InvestmentPaperOrderPo extends BaseAuditablePo {

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;
    @Column(name = "account_id", nullable = false)
    private Long accountId;
    @Column(name = "intent_id", nullable = false, updatable = false)
    private Long intentId;
    @Column(name = "client_order_id", nullable = false, length = 128, updatable = false)
    private String clientOrderId;
    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;
    @Column(name = "origin", nullable = false, length = 32)
    private String origin;
    @Column(name = "position_action", nullable = false, length = 32)
    private String positionAction;
    @Column(name = "side", nullable = false, length = 16)
    private String side;
    @Column(name = "order_type", nullable = false, length = 16)
    private String orderType;
    @Column(name = "time_in_force", nullable = false, length = 16)
    private String timeInForce;
    @Column(name = "quantity", nullable = false, precision = 38, scale = 18)
    private BigDecimal quantity;
    @Column(name = "remaining_quantity", nullable = false, precision = 38, scale = 18)
    private BigDecimal remainingQuantity;
    @Column(name = "leverage", nullable = false, precision = 24, scale = 12)
    private BigDecimal leverage;
    @Column(name = "limit_price", precision = 38, scale = 18)
    private BigDecimal limitPrice;
    @Column(name = "reduce_only", nullable = false)
    private boolean reduceOnly;
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;
    @Column(name = "matched_at")
    private Instant matchedAt;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    @Column(name = "rejection_code", length = 64)
    private String rejectionCode;
    @Column(name = "rejection_message", length = 2000)
    private String rejectionMessage;
}
