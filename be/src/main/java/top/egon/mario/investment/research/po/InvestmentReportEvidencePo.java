package top.egon.mario.investment.research.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Immutable evidence owned by exactly one persisted research-report version.
 */
@Getter
@Setter
@Entity
@Table(name = "investment_report_evidence")
public class InvestmentReportEvidencePo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false, updatable = false)
    private Long reportId;

    @Column(name = "evidence_type", nullable = false, length = 64, updatable = false)
    private String evidenceType;

    @Column(name = "source_id", nullable = false, updatable = false)
    private Long sourceId;

    @Column(name = "instrument_id", updatable = false)
    private Long instrumentId;

    @Column(name = "data_start_time", nullable = false, updatable = false)
    private Instant dataStartTime;

    @Column(name = "data_end_time", nullable = false, updatable = false)
    private Instant dataEndTime;

    @Column(name = "data_as_of", nullable = false, updatable = false)
    private Instant dataAsOf;

    @Column(name = "source_reference", nullable = false, length = 512, updatable = false)
    private String sourceReference;

    @Column(name = "payload_hash", nullable = false, length = 128, updatable = false)
    private String payloadHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb", updatable = false)
    private String metadataJson = "{}";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
