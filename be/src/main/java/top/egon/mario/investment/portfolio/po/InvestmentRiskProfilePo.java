package top.egon.mario.investment.portfolio.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.common.entity.BaseAuditablePo;

import java.math.BigDecimal;

/**
 * Explicit account risk limits. Units are encoded in field names and never hidden in JSON.
 */
@Getter
@Setter
@Entity
@Table(name = "investment_risk_profile", uniqueConstraints = {
        @UniqueConstraint(name = "uk_investment_risk_profile_account", columnNames = "account_id")
})
public class InvestmentRiskProfilePo extends BaseAuditablePo {

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(name = "max_leverage", nullable = false, precision = 24, scale = 12)
    private BigDecimal maxLeverage;

    @Column(name = "max_order_notional", nullable = false, precision = 38, scale = 18)
    private BigDecimal maxOrderNotional;

    @Column(name = "max_position_notional", nullable = false, precision = 38, scale = 18)
    private BigDecimal maxPositionNotional;

    @Column(name = "max_gross_exposure_notional", nullable = false, precision = 38, scale = 18)
    private BigDecimal maxGrossExposureNotional;

    @Column(name = "max_open_positions", nullable = false)
    private Long maxOpenPositions;

    @Column(name = "max_daily_loss_amount", nullable = false, precision = 38, scale = 18)
    private BigDecimal maxDailyLossAmount;

    @Column(name = "max_drawdown_ratio", nullable = false, precision = 24, scale = 12)
    private BigDecimal maxDrawdownRatio;

    @Column(name = "max_orders_per_hour", nullable = false)
    private Long maxOrdersPerHour;

    @Column(name = "cooldown_seconds", nullable = false)
    private Long cooldownSeconds;

    @Column(name = "max_market_data_age_seconds", nullable = false)
    private Long maxMarketDataAgeSeconds;

    @Column(name = "max_slippage_bps", nullable = false, precision = 24, scale = 12)
    private BigDecimal maxSlippageBps;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings_json", nullable = false, columnDefinition = "jsonb")
    private String settingsJson = "{}";
}
