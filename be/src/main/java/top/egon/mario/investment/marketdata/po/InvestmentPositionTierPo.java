package top.egon.mario.investment.marketdata.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Historical provider position tier observed at a point in time.
 */
@Getter
@Setter
@Entity
@Table(name = "investment_position_tier", uniqueConstraints = {
        @UniqueConstraint(name = "uk_investment_position_tier_snapshot",
                columnNames = {"source_id", "instrument_id", "observed_at", "tier_level"})
})
public class InvestmentPositionTierPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "tier_level", nullable = false)
    private int tierLevel;

    @Column(name = "start_notional", nullable = false, precision = 38, scale = 18)
    private BigDecimal startNotional;

    @Column(name = "end_notional", nullable = false, precision = 38, scale = 18)
    private BigDecimal endNotional;

    @Column(name = "max_leverage", nullable = false, precision = 24, scale = 12)
    private BigDecimal maxLeverage;

    @Column(name = "maintenance_margin_rate", nullable = false, precision = 24, scale = 12)
    private BigDecimal maintenanceMarginRate;

    @Column(name = "source_hash", nullable = false, length = 128)
    private String sourceHash;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
}
