package top.egon.mario.investment.marketdata.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.common.entity.BaseAuditablePo;
import top.egon.mario.investment.common.model.ProductType;

import java.math.BigDecimal;

/**
 * Audited logical market-data source. Credentials are deliberately not persisted here.
 */
@Getter
@Setter
@Entity
@Table(name = "investment_data_source", uniqueConstraints = {
        @UniqueConstraint(name = "uk_investment_data_source_code", columnNames = "code")
})
public class InvestmentDataSourcePo extends BaseAuditablePo {

    @Column(name = "venue_id", nullable = false)
    private Long venueId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "provider_type", nullable = false, length = 64)
    private String providerType;

    @Column(name = "api_family", nullable = false, length = 64)
    private String apiFamily;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 32)
    private ProductType productType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "capabilities_json", nullable = false, columnDefinition = "jsonb")
    private String capabilitiesJson = "[]";

    @Column(name = "rate_limit_per_second", nullable = false, precision = 24, scale = 12)
    private BigDecimal rateLimitPerSecond;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings_json", nullable = false, columnDefinition = "jsonb")
    private String settingsJson = "{}";
}
