package top.egon.mario.investment.portfolio.po;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "investment_account_snapshot")
public class InvestmentAccountSnapshotPo {

    @EmbeddedId
    private InvestmentAccountSnapshotId id;
    @Column(name = "wallet_balance", nullable = false, precision = 38, scale = 18)
    private BigDecimal walletBalance;
    @Column(name = "used_margin", nullable = false, precision = 38, scale = 18)
    private BigDecimal usedMargin;
    @Column(name = "maintenance_margin", nullable = false, precision = 38, scale = 18)
    private BigDecimal maintenanceMargin;
    @Column(name = "unrealized_pnl", nullable = false, precision = 38, scale = 18)
    private BigDecimal unrealizedPnl;
    @Column(name = "equity", nullable = false, precision = 38, scale = 18)
    private BigDecimal equity;
    @Column(name = "available_balance", nullable = false, precision = 38, scale = 18)
    private BigDecimal availableBalance;
    @Column(name = "gross_exposure", nullable = false, precision = 38, scale = 18)
    private BigDecimal grossExposure;
    @Column(name = "total_return", nullable = false, precision = 24, scale = 12)
    private BigDecimal totalReturn;
    @Column(name = "drawdown", nullable = false, precision = 24, scale = 12)
    private BigDecimal drawdown;
    @Column(name = "position_count", nullable = false)
    private Long positionCount;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
