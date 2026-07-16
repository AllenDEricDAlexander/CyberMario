package top.egon.mario.investment.marketdata.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;

import java.time.Instant;

/**
 * Lockable ingestion dimension and its progress state.
 */
@Getter
@Setter
@Entity
@Table(name = "investment_ingest_cursor", uniqueConstraints = {
        @UniqueConstraint(name = "uk_investment_ingest_cursor_dimension",
                columnNames = {"source_id", "instrument_id", "data_type", "price_type", "interval_code"})
})
public class InvestmentIngestCursorPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @Column(name = "next_start_time")
    private Instant nextStartTime;

    @Column(name = "last_success_time")
    private Instant lastSuccessTime;

    @Column(name = "last_checksum", length = 128)
    private String lastChecksum;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "IDLE";

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
