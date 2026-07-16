package top.egon.mario.investment.marketdata.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.common.entity.BaseAuditablePo;

import java.time.Instant;

/**
 * Audited mapping between an internal instrument and a provider symbol.
 */
@Getter
@Setter
@Entity
@Table(name = "investment_instrument_source", uniqueConstraints = {
        @UniqueConstraint(name = "uk_investment_instrument_source_external",
                columnNames = {"source_id", "external_product_type", "external_symbol"}),
        @UniqueConstraint(name = "uk_investment_instrument_source_mapping",
                columnNames = {"instrument_id", "source_id"})
})
public class InvestmentInstrumentSourcePo extends BaseAuditablePo {

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "external_symbol", nullable = false, length = 128)
    private String externalSymbol;

    @Column(name = "external_product_type", nullable = false, length = 64)
    private String externalProductType;

    @Column(name = "source_status", nullable = false, length = 32)
    private String sourceStatus = "ACTIVE";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_metadata_json", nullable = false, columnDefinition = "jsonb")
    private String rawMetadataJson = "{}";

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;
}
