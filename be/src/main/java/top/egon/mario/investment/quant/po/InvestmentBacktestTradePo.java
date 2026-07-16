package top.egon.mario.investment.quant.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "investment_backtest_trade")
public class InvestmentBacktestTradePo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "run_id", nullable = false, updatable = false)
    private Long runId;
    @Column(name = "instrument_id", nullable = false, updatable = false)
    private Long instrumentId;
    @Column(name = "position_side", nullable = false, length = 16, updatable = false)
    private String positionSide;
    @Column(name = "entry_time", nullable = false, updatable = false)
    private Instant entryTime;
    @Column(name = "exit_time", nullable = false, updatable = false)
    private Instant exitTime;
    @Column(name = "entry_price", nullable = false, precision = 38, scale = 18, updatable = false)
    private BigDecimal entryPrice;
    @Column(name = "exit_price", nullable = false, precision = 38, scale = 18, updatable = false)
    private BigDecimal exitPrice;
    @Column(name = "quantity", nullable = false, precision = 38, scale = 18, updatable = false)
    private BigDecimal quantity;
    @Column(name = "leverage", nullable = false, precision = 24, scale = 12, updatable = false)
    private BigDecimal leverage;
    @Column(name = "gross_pnl", nullable = false, precision = 38, scale = 18, updatable = false)
    private BigDecimal grossPnl;
    @Column(name = "fee_amount", nullable = false, precision = 38, scale = 18, updatable = false)
    private BigDecimal feeAmount;
    @Column(name = "funding_amount", nullable = false, precision = 38, scale = 18, updatable = false)
    private BigDecimal fundingAmount;
    @Column(name = "net_pnl", nullable = false, precision = 38, scale = 18, updatable = false)
    private BigDecimal netPnl;
    @Column(name = "exit_reason", nullable = false, length = 64, updatable = false)
    private String exitReason;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
