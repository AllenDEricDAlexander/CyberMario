package top.egon.mario.investment.trading.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "investment_margin_ledger", uniqueConstraints = {
        @UniqueConstraint(name = "uk_investment_margin_ledger_sequence",
                columnNames = {"account_id", "sequence_no"}),
        @UniqueConstraint(name = "uk_investment_margin_ledger_idempotency", columnNames = "idempotency_key")
})
public class InvestmentMarginLedgerPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;
    @Column(name = "sequence_no", nullable = false, updatable = false)
    private Long sequenceNo;
    @Column(name = "event_type", nullable = false, length = 32, updatable = false)
    private String eventType;
    @Column(name = "asset", nullable = false, length = 32, updatable = false)
    private String asset;
    @Column(name = "amount", nullable = false, precision = 38, scale = 18, updatable = false)
    private BigDecimal amount;
    @Column(name = "balance_after", nullable = false, precision = 38, scale = 18, updatable = false)
    private BigDecimal balanceAfter;
    @Column(name = "instrument_id", updatable = false)
    private Long instrumentId;
    @Column(name = "reference_type", nullable = false, length = 64, updatable = false)
    private String referenceType;
    @Column(name = "reference_id", nullable = false, length = 128, updatable = false)
    private String referenceId;
    @Column(name = "idempotency_key", nullable = false, length = 128, updatable = false)
    private String idempotencyKey;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details_json", nullable = false, columnDefinition = "jsonb", updatable = false)
    private String detailsJson = "{}";
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
