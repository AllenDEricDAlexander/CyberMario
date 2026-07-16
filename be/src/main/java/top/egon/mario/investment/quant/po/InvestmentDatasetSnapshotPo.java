package top.egon.mario.investment.quant.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Immutable manifest for one reproducible market-data view.
 */
@Getter
@Setter
@Entity
@Table(name = "investment_dataset_snapshot")
public class InvestmentDatasetSnapshotPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private Long workspaceId;

    @Column(name = "source_id", nullable = false, updatable = false)
    private Long sourceId;

    @Column(name = "start_time", nullable = false, updatable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false, updatable = false)
    private Instant endTime;

    @Column(name = "data_as_of", nullable = false, updatable = false)
    private Instant dataAsOf;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "intervals_json", nullable = false, columnDefinition = "jsonb", updatable = false)
    private String intervalsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "price_types_json", nullable = false, columnDefinition = "jsonb", updatable = false)
    private String priceTypesJson;

    @Column(name = "contract_spec_hash", nullable = false, length = 64, updatable = false)
    private String contractSpecHash;

    @Column(name = "position_tier_hash", nullable = false, length = 64, updatable = false)
    private String positionTierHash;

    @Column(name = "funding_data_hash", nullable = false, length = 64, updatable = false)
    private String fundingDataHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contract_spec_snapshot_json", nullable = false, columnDefinition = "jsonb", updatable = false)
    private String contractSpecSnapshotJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "position_tier_snapshot_json", nullable = false, columnDefinition = "jsonb", updatable = false)
    private String positionTierSnapshotJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fee_model_snapshot_json", nullable = false, columnDefinition = "jsonb", updatable = false)
    private String feeModelSnapshotJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "slippage_model_snapshot_json", nullable = false, columnDefinition = "jsonb", updatable = false)
    private String slippageModelSnapshotJson;

    @Column(name = "dataset_hash", nullable = false, length = 64, updatable = false)
    private String datasetHash;

    @Column(name = "quality_status", nullable = false, length = 32)
    private String qualityStatus;

    @Column(name = "artifact_uri", length = 2000)
    private String artifactUri;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;
}
