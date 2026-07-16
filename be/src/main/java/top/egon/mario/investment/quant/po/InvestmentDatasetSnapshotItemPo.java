package top.egon.mario.investment.quant.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Immutable hash and revision range for one dataset dimension.
 */
@Getter
@Setter
@Entity
@Table(name = "investment_dataset_snapshot_item")
public class InvestmentDatasetSnapshotItemPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_id", nullable = false, updatable = false)
    private Long snapshotId;

    @Column(name = "instrument_id", nullable = false, updatable = false)
    private Long instrumentId;

    @Column(name = "data_type", nullable = false, length = 32, updatable = false)
    private String dataType;

    @Column(name = "price_type", nullable = false, length = 32, updatable = false)
    private String priceType;

    @Column(name = "interval_code", nullable = false, length = 32, updatable = false)
    private String intervalCode;

    @Column(name = "first_time", nullable = false, updatable = false)
    private Instant firstTime;

    @Column(name = "last_time", nullable = false, updatable = false)
    private Instant lastTime;

    @Column(name = "row_count", nullable = false, updatable = false)
    private long rowCount;

    @Column(name = "max_revision", nullable = false, updatable = false)
    private long maxRevision;

    @Column(name = "data_hash", nullable = false, length = 64, updatable = false)
    private String dataHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
