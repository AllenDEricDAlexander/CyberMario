package top.egon.mario.investment.quant.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "investment_backtest_event")
public class InvestmentBacktestEventPo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "run_id", nullable = false, updatable = false)
    private Long runId;
    @Column(name = "instrument_id", updatable = false)
    private Long instrumentId;
    @Column(name = "event_type", nullable = false, length = 32, updatable = false)
    private String eventType;
    @Column(name = "event_time", nullable = false, updatable = false)
    private Instant eventTime;
    @Column(name = "amount", precision = 38, scale = 18, updatable = false)
    private BigDecimal amount;
    @Column(name = "balance_after", precision = 38, scale = 18, updatable = false)
    private BigDecimal balanceAfter;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details_json", nullable = false, columnDefinition = "jsonb", updatable = false)
    private String detailsJson;
    @Column(name = "sequence_no", nullable = false, updatable = false)
    private Long sequenceNo;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
