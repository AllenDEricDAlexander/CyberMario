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

/**
 * Audited trading venue owned by the platform market-data catalog.
 */
@Getter
@Setter
@Entity
@Table(name = "investment_venue", uniqueConstraints = {
        @UniqueConstraint(name = "uk_investment_venue_code", columnNames = "code")
})
public class InvestmentVenuePo extends BaseAuditablePo {

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "UTC";

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
