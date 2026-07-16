package top.egon.mario.investment.quant.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.common.entity.BaseAuditablePo;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "investment_backtest_run")
public class InvestmentBacktestRunPo extends BaseAuditablePo {

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;
    @Column(name = "job_id", nullable = false, updatable = false)
    private Long jobId;
    @Column(name = "strategy_release_id", nullable = false, updatable = false)
    private Long strategyReleaseId;
    @Column(name = "dataset_snapshot_id", nullable = false, updatable = false)
    private Long datasetSnapshotId;
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    @Column(name = "initial_equity", nullable = false, precision = 38, scale = 18, updatable = false)
    private BigDecimal initialEquity;
    @Column(name = "base_currency", nullable = false, length = 32, updatable = false)
    private String baseCurrency;
    @Column(name = "margin_mode", nullable = false, length = 32, updatable = false)
    private String marginMode;
    @Column(name = "position_mode", nullable = false, length = 32, updatable = false)
    private String positionMode;
    @Column(name = "fee_model_code", nullable = false, length = 128, updatable = false)
    private String feeModelCode;
    @Column(name = "slippage_model_code", nullable = false, length = 128, updatable = false)
    private String slippageModelCode;
    @Column(name = "matching_model_code", nullable = false, length = 128, updatable = false)
    private String matchingModelCode;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "finished_at")
    private Instant finishedAt;
    @Column(name = "error_code", length = 64)
    private String errorCode;
    @Column(name = "error_message", length = 2000)
    private String errorMessage;
    @Column(name = "total_return", precision = 24, scale = 12)
    private BigDecimal totalReturn;
    @Column(name = "annualized_return", precision = 24, scale = 12)
    private BigDecimal annualizedReturn;
    @Column(name = "max_drawdown", precision = 24, scale = 12)
    private BigDecimal maxDrawdown;
    @Column(name = "sharpe_ratio", precision = 24, scale = 12)
    private BigDecimal sharpeRatio;
    @Column(name = "sortino_ratio", precision = 24, scale = 12)
    private BigDecimal sortinoRatio;
    @Column(name = "win_rate", precision = 24, scale = 12)
    private BigDecimal winRate;
    @Column(name = "profit_factor", precision = 38, scale = 18)
    private BigDecimal profitFactor;
    @Column(name = "turnover", precision = 38, scale = 18)
    private BigDecimal turnover;
    @Column(name = "trade_count")
    private Long tradeCount;
    @Column(name = "total_fee", precision = 38, scale = 18)
    private BigDecimal totalFee;
    @Column(name = "total_funding", precision = 38, scale = 18)
    private BigDecimal totalFunding;
    @Column(name = "liquidation_count")
    private Long liquidationCount;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_metrics_json", nullable = false, columnDefinition = "jsonb")
    private String extraMetricsJson = "{}";
}
