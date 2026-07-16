package top.egon.mario.investment.marketdata.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.common.entity.BaseAuditablePo;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;

import java.time.Instant;

/**
 * Audited quality fact emitted by a durable ingestion job.
 */
@Getter
@Setter
@Entity
@Table(name = "investment_data_quality_issue")
public class InvestmentDataQualityIssuePo extends BaseAuditablePo {

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(name = "data_type", nullable = false, length = 64)
    private String dataType;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_type", nullable = false, length = 32)
    private PriceType priceType = PriceType.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "interval_code", nullable = false, length = 32)
    private BarInterval interval = BarInterval.NONE;

    @Column(name = "point_time", nullable = false)
    private Instant pointTime;

    @Column(name = "issue_code", nullable = false, length = 64)
    private String issueCode;

    @Column(name = "severity", nullable = false, length = 32)
    private String severity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details_json", nullable = false, columnDefinition = "jsonb")
    private String detailsJson = "{}";

    @Column(name = "resolution_status", nullable = false, length = 32)
    private String resolutionStatus = "OPEN";

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
