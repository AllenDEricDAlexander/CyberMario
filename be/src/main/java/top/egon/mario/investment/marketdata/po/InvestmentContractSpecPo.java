package top.egon.mario.investment.marketdata.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Current normalized contract specification for one instrument.
 */
@Getter
@Setter
@Entity
@Table(name = "investment_contract_spec")
public class InvestmentContractSpecPo {

    @Id
    @Column(name = "instrument_id")
    private Long instrumentId;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "price_precision", nullable = false)
    private int pricePrecision;

    @Column(name = "quantity_precision", nullable = false)
    private int quantityPrecision;

    @Column(name = "price_end_step", nullable = false, precision = 38, scale = 18)
    private BigDecimal priceEndStep;

    @Column(name = "quantity_step", nullable = false, precision = 38, scale = 18)
    private BigDecimal quantityStep;

    @Column(name = "contract_multiplier", nullable = false, precision = 38, scale = 18)
    private BigDecimal contractMultiplier;

    @Column(name = "min_trade_quantity", nullable = false, precision = 38, scale = 18)
    private BigDecimal minTradeQuantity;

    @Column(name = "min_trade_notional", nullable = false, precision = 38, scale = 18)
    private BigDecimal minTradeNotional;

    @Column(name = "max_market_order_quantity", nullable = false, precision = 38, scale = 18)
    private BigDecimal maxMarketOrderQuantity;

    @Column(name = "max_limit_order_quantity", nullable = false, precision = 38, scale = 18)
    private BigDecimal maxLimitOrderQuantity;

    @Column(name = "maker_fee_rate", nullable = false, precision = 24, scale = 12)
    private BigDecimal makerFeeRate;

    @Column(name = "taker_fee_rate", nullable = false, precision = 24, scale = 12)
    private BigDecimal takerFeeRate;

    @Column(name = "min_leverage", nullable = false, precision = 24, scale = 12)
    private BigDecimal minLeverage;

    @Column(name = "max_leverage", nullable = false, precision = 24, scale = 12)
    private BigDecimal maxLeverage;

    @Column(name = "funding_interval_hours", nullable = false)
    private int fundingIntervalHours;

    @Column(name = "buy_limit_price_ratio", nullable = false, precision = 24, scale = 12)
    private BigDecimal buyLimitPriceRatio;

    @Column(name = "sell_limit_price_ratio", nullable = false, precision = 24, scale = 12)
    private BigDecimal sellLimitPriceRatio;

    @Column(name = "source_updated_at")
    private Instant sourceUpdatedAt;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    @Column(name = "revision", nullable = false)
    private long revision = 1L;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_metadata_json", nullable = false, columnDefinition = "jsonb")
    private String rawMetadataJson = "{}";
}
