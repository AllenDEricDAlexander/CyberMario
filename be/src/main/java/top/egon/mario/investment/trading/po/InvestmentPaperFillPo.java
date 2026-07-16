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

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "investment_paper_fill", uniqueConstraints = {
        @UniqueConstraint(name = "uk_investment_paper_fill_number", columnNames = {"order_id", "fill_no"})
})
public class InvestmentPaperFillPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;
    @Column(name = "fill_no", nullable = false, updatable = false)
    private Long fillNo;
    @Column(name = "instrument_id", nullable = false, updatable = false)
    private Long instrumentId;
    @Column(name = "position_action", nullable = false, length = 32, updatable = false)
    private String positionAction;
    @Column(name = "side", nullable = false, length = 16, updatable = false)
    private String side;
    @Column(name = "fill_price", nullable = false, precision = 38, scale = 18, updatable = false)
    private BigDecimal fillPrice;
    @Column(name = "quantity", nullable = false, precision = 38, scale = 18, updatable = false)
    private BigDecimal quantity;
    @Column(name = "notional", nullable = false, precision = 38, scale = 18, updatable = false)
    private BigDecimal notional;
    @Column(name = "fee_rate", nullable = false, precision = 24, scale = 12, updatable = false)
    private BigDecimal feeRate;
    @Column(name = "fee_amount", nullable = false, precision = 38, scale = 18, updatable = false)
    private BigDecimal feeAmount;
    @Column(name = "fee_asset", nullable = false, length = 32, updatable = false)
    private String feeAsset;
    @Column(name = "liquidity", nullable = false, length = 16, updatable = false)
    private String liquidity;
    @Column(name = "filled_at", nullable = false, updatable = false)
    private Instant filledAt;
    @Column(name = "market_bar_open_time", updatable = false)
    private Instant marketBarOpenTime;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
