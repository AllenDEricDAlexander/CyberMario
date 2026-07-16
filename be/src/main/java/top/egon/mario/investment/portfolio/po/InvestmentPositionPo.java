package top.egon.mario.investment.portfolio.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "investment_position", uniqueConstraints = {
        @UniqueConstraint(name = "uk_investment_position_instrument",
                columnNames = {"account_id", "instrument_id"})
})
public class InvestmentPositionPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;
    @Column(name = "instrument_id", nullable = false, updatable = false)
    private Long instrumentId;
    @Column(name = "position_side", nullable = false, length = 16)
    private String positionSide;
    @Column(name = "quantity", nullable = false, precision = 38, scale = 18)
    private BigDecimal quantity;
    @Column(name = "entry_price", nullable = false, precision = 38, scale = 18)
    private BigDecimal entryPrice;
    @Column(name = "leverage", nullable = false, precision = 24, scale = 12)
    private BigDecimal leverage;
    @Column(name = "isolated_margin", nullable = false, precision = 38, scale = 18)
    private BigDecimal isolatedMargin;
    @Column(name = "maintenance_margin_rate", nullable = false, precision = 24, scale = 12)
    private BigDecimal maintenanceMarginRate;
    @Column(name = "maintenance_margin", nullable = false, precision = 38, scale = 18)
    private BigDecimal maintenanceMargin;
    @Column(name = "liquidation_price", nullable = false, precision = 38, scale = 18)
    private BigDecimal liquidationPrice;
    @Column(name = "realized_pnl", nullable = false, precision = 38, scale = 18)
    private BigDecimal realizedPnl = BigDecimal.ZERO;
    @Column(name = "funding_pnl", nullable = false, precision = 38, scale = 18)
    private BigDecimal fundingPnl = BigDecimal.ZERO;
    @Column(name = "last_fill_at")
    private Instant lastFillAt;
    @Column(name = "last_margin_check_at")
    private Instant lastMarginCheckAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
