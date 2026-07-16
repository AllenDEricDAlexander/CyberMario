package top.egon.mario.investment.marketdata.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.common.entity.BaseAuditablePo;
import top.egon.mario.investment.common.model.ContractType;
import top.egon.mario.investment.common.model.ProductType;

import java.time.Instant;

/**
 * Audited internal instrument identity independent of provider symbols.
 */
@Getter
@Setter
@Entity
@Table(name = "investment_instrument", uniqueConstraints = {
        @UniqueConstraint(name = "uk_investment_instrument_business",
                columnNames = {"venue_id", "product_type", "symbol"})
})
public class InvestmentInstrumentPo extends BaseAuditablePo {

    @Column(name = "venue_id", nullable = false)
    private Long venueId;

    @Column(name = "market_type", nullable = false, length = 32)
    private String marketType = "FUTURES";

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 32)
    private ProductType productType;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false, length = 32)
    private ContractType contractType;

    @Column(name = "symbol", nullable = false, length = 128)
    private String symbol;

    @Column(name = "base_asset", nullable = false, length = 32)
    private String baseAsset;

    @Column(name = "quote_asset", nullable = false, length = 32)
    private String quoteAsset;

    @Column(name = "settlement_asset", nullable = false, length = 32)
    private String settlementAsset;

    @Column(name = "margin_asset", nullable = false, length = 32)
    private String marginAsset;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "launch_time")
    private Instant launchTime;

    @Column(name = "delivery_start_time")
    private Instant deliveryStartTime;

    @Column(name = "delivery_time")
    private Instant deliveryTime;

    @Column(name = "off_time")
    private Instant offTime;
}
