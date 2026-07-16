package top.egon.mario.investment.marketdata.ingest;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import top.egon.mario.investment.common.job.InvestmentJobRetryableException;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.po.InvestmentIngestCursorPo;
import top.egon.mario.investment.marketdata.repository.InvestmentIngestCursorRepository;

import java.time.Instant;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;

/**
 * Updates non-revisioned ingestion cursors while holding the same pessimistic dimension fence.
 */
@Service
public class MarketDataCursorService {

    private final InvestmentIngestCursorRepository cursorRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Clock fallbackClock;

    public MarketDataCursorService(InvestmentIngestCursorRepository cursorRepository,
                                   NamedParameterJdbcTemplate jdbcTemplate,
                                   Clock fallbackClock) {
        this.cursorRepository = cursorRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.fallbackClock = fallbackClock;
    }

    public void succeed(MarketDataDimension dimension, String dataType, PriceType priceType, BarInterval interval,
                        Instant nextStartTime, String checksum) {
        LockedCursor cursor = lock(dimension, dataType, priceType, interval);
        completeLocked(cursor, nextStartTime, checksum);
    }

    /**
     * Atomically creates a cursor dimension without overwriting an existing ingestion state.
     */
    public void seedIfAbsent(MarketDataDimension dimension, String dataType, PriceType priceType,
                             BarInterval interval) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("sourceId", dimension.sourceId())
                .addValue("instrumentId", dimension.instrumentId())
                .addValue("dataType", dataType)
                .addValue("priceType", priceType.name())
                .addValue("interval", interval.name());
        if (isPostgresql()) {
            jdbcTemplate.update("""
                    insert into investment_ingest_cursor
                        (source_id, instrument_id, data_type, price_type, interval_code,
                         status, updated_at, version)
                    values (:sourceId, :instrumentId, :dataType, :priceType, :interval,
                            'IDLE', clock_timestamp(), 0)
                    on conflict (source_id, instrument_id, data_type, price_type, interval_code) do nothing
                    """, parameters);
            return;
        }
        parameters.addValue("updatedAt", fallbackClock.instant());
        try {
            jdbcTemplate.update("""
                    insert into investment_ingest_cursor
                        (source_id, instrument_id, data_type, price_type, interval_code,
                         status, updated_at, version)
                    select :sourceId, :instrumentId, :dataType, :priceType, :interval,
                           'IDLE', :updatedAt, 0
                    where not exists (
                        select 1 from investment_ingest_cursor
                        where source_id = :sourceId and instrument_id = :instrumentId
                          and data_type = :dataType and price_type = :priceType
                          and interval_code = :interval
                    )
                    """, parameters);
        } catch (DuplicateKeyException ignored) {
            // H2 keeps the transaction usable after a concurrent unique-key loser; PostgreSQL uses ON CONFLICT.
        }
    }

    public LockedCursor lock(MarketDataDimension dimension, String dataType, PriceType priceType,
                             BarInterval interval) {
        InvestmentIngestCursorPo cursor = cursorRepository.findDimensionForUpdate(dimension.sourceId(),
                        dimension.instrumentId(), dataType, priceType, interval)
                .orElseThrow(() -> new InvestmentJobRetryableException("MARKET_CURSOR_NOT_READY",
                        "Market-data cursor is awaiting contract metadata: " + dataType));
        Instant authoritativeAt = authoritativeTimeAfterLock();
        return new LockedCursor(cursor, maximum(authoritativeAt, cursor.getLastSuccessTime(), cursor.getUpdatedAt()));
    }

    public void completeLocked(LockedCursor lockedCursor, Instant nextStartTime, String checksum) {
        InvestmentIngestCursorPo cursor = lockedCursor.cursor();
        if (nextStartTime != null
                && (cursor.getNextStartTime() == null || nextStartTime.isAfter(cursor.getNextStartTime()))) {
            cursor.setNextStartTime(nextStartTime);
        }
        if (cursor.getLastSuccessTime() == null
                || lockedCursor.completedAt().isAfter(cursor.getLastSuccessTime())) {
            cursor.setLastSuccessTime(lockedCursor.completedAt());
        }
        cursor.setLastChecksum(checksum);
        cursor.setStatus("SUCCEEDED");
        cursor.setLastError(null);
        if (cursor.getUpdatedAt() == null || lockedCursor.completedAt().isAfter(cursor.getUpdatedAt())) {
            cursor.setUpdatedAt(lockedCursor.completedAt());
        }
    }

    private Instant authoritativeTimeAfterLock() {
        if (isPostgresql()) {
            return jdbcTemplate.queryForObject("select clock_timestamp() as authoritative_at", Map.of(),
                    (resultSet, rowNumber) -> {
                        Object value = resultSet.getObject("authoritative_at");
                        if (value instanceof java.time.OffsetDateTime offsetDateTime) {
                            return offsetDateTime.toInstant();
                        }
                        if (value instanceof java.sql.Timestamp timestamp) {
                            return timestamp.toInstant();
                        }
                        if (value instanceof Instant instant) {
                            return instant;
                        }
                        throw new IllegalStateException("Unsupported authoritative database timestamp: " + value);
                    });
        }
        return fallbackClock.instant();
    }

    private boolean isPostgresql() {
        Boolean postgresql = jdbcTemplate.getJdbcTemplate().execute((ConnectionCallback<Boolean>) connection ->
                "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName()));
        return Boolean.TRUE.equals(postgresql);
    }

    private Instant maximum(Instant first, Instant... candidates) {
        Instant maximum = Objects.requireNonNull(first, "first");
        for (Instant candidate : candidates) {
            if (candidate != null && candidate.isAfter(maximum)) {
                maximum = candidate;
            }
        }
        return maximum;
    }

    /**
     * Cursor fence whose completion timestamp can only be obtained after the pessimistic lock succeeds.
     */
    public record LockedCursor(InvestmentIngestCursorPo cursor, Instant completedAt) {

        public LockedCursor {
            Objects.requireNonNull(cursor, "cursor");
            Objects.requireNonNull(completedAt, "completedAt");
        }
    }
}
