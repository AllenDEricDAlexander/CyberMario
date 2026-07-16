package top.egon.mario.investment.marketdata.repository.jdbc;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarDailyRow;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarDailyWrite;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarIntradayRow;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarIntradayWrite;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketDataWriteContext;
import top.egon.mario.investment.marketdata.repository.jdbc.model.RevisionBatchResult;

import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * JDBC persistence adapter for high-volume daily and intraday bar revisions.
 */
@Repository
public class MarketBarJdbcRepository {

    private static final String INTRADAY_COLUMNS = """
            source_id, instrument_id, price_type, interval_code, open_time, close_time,
            open_price, high_price, low_price, close_price, base_volume, quote_volume,
            is_closed, source_updated_at, ingested_at, revision, valid_from, valid_to, checksum
            """;

    private static final String DAILY_COLUMNS = """
            source_id, instrument_id, price_type, bar_date,
            open_price, high_price, low_price, close_price, base_volume, quote_volume,
            is_closed, source_updated_at, ingested_at, revision, valid_from, valid_to, checksum
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MarketRevisionTransactionSupport revisionSupport;

    public MarketBarJdbcRepository(NamedParameterJdbcTemplate jdbcTemplate,
                                   MarketRevisionTransactionSupport revisionSupport) {
        this.jdbcTemplate = jdbcTemplate;
        this.revisionSupport = revisionSupport;
    }

    @Transactional
    public RevisionBatchResult writeIntradayRevision(MarketDataWriteContext context,
                                                     MarketBarIntradayWrite value) {
        return writeIntradayBatch(context, List.of(value));
    }

    /**
     * Persists one normalized page while holding the dimension cursor lock.
     */
    @Transactional
    public RevisionBatchResult writeIntradayBatch(MarketDataWriteContext context,
                                                  List<MarketBarIntradayWrite> values) {
        Objects.requireNonNull(context, "context");
        List<MarketBarIntradayWrite> ordered = validateIntradayBatch(values);
        MarketBarIntradayWrite dimension = ordered.getFirst();
        MarketRevisionTransactionSupport.RevisionFence fence = revisionSupport.lockCursor(
                dimension.sourceId(), dimension.instrumentId(), MarketRevisionTransactionSupport.INTRADAY_BAR,
                dimension.priceType(), dimension.interval());

        List<SqlParameterSource> closes = new ArrayList<>();
        List<SqlParameterSource> inserts = new ArrayList<>();
        List<RevisionQuality> qualityIssues = new ArrayList<>();
        Map<Instant, CurrentRevision> currentRevisions = currentIntraday(ordered);
        Instant revisionEffectiveAt = revisionSupport.revisionEffectiveAt(fence, ordered.stream()
                .filter(value -> currentRevisions.containsKey(value.openTime()))
                .filter(value -> !value.checksum().equals(currentRevisions.get(value.openTime()).checksum()))
                .map(value -> currentRevisions.get(value.openTime()).validFrom())
                .toList());
        int inserted = 0;
        int revised = 0;
        int unchanged = 0;
        long maxRevision = 0;
        for (MarketBarIntradayWrite value : ordered) {
            Optional<CurrentRevision> current = Optional.ofNullable(currentRevisions.get(value.openTime()));
            if (current.isPresent() && current.orElseThrow().checksum().equals(value.checksum())) {
                unchanged++;
                maxRevision = Math.max(maxRevision, current.orElseThrow().revision());
                continue;
            }
            long nextRevision = current.map(CurrentRevision::revision).orElse(0L) + 1L;
            if (current.isPresent()) {
                CurrentRevision old = current.orElseThrow();
                closes.add(intradayKey(value)
                        .addValue("revision", old.revision())
                        .addValue("checksum", old.checksum())
                        .addValue("validTo", JdbcMarketDataSupport.instantParameter(revisionEffectiveAt)));
                if (old.closed()) {
                    qualityIssues.add(new RevisionQuality(value.sourceId(), value.instrumentId(),
                            MarketRevisionTransactionSupport.INTRADAY_BAR, value.priceType(), value.interval(),
                            value.openTime(), old.revision(), old.checksum(), nextRevision, value.checksum()));
                }
                revised++;
            } else {
                inserted++;
            }
            inserts.add(intradayParameters(value, nextRevision, revisionEffectiveAt));
            maxRevision = Math.max(maxRevision, nextRevision);
        }

        verifyBatchCounts(jdbcTemplate.batchUpdate("""
                update investment_market_bar_intraday
                set revision_slot = revision, valid_to = :validTo
                where source_id = :sourceId and instrument_id = :instrumentId
                  and price_type = :priceType and interval_code = :interval
                  and open_time = :openTime and revision_slot = 0
                  and revision = :revision and checksum = :checksum
                """, closes.toArray(SqlParameterSource[]::new)), "close intraday bar revision");
        verifyBatchCounts(jdbcTemplate.batchUpdate("""
                insert into investment_market_bar_intraday (
                    source_id, instrument_id, price_type, interval_code, open_time, close_time,
                    open_price, high_price, low_price, close_price, base_volume, quote_volume,
                    is_closed, source_updated_at, ingested_at, revision, revision_slot,
                    valid_from, valid_to, checksum
                ) values (
                    :sourceId, :instrumentId, :priceType, :interval, :openTime, :closeTime,
                    :openPrice, :highPrice, :lowPrice, :closePrice, :baseVolume, :quoteVolume,
                    :closed, :sourceUpdatedAt, :ingestedAt, :revision, 0,
                    :validFrom, null, :checksum
                )
                """, inserts.toArray(SqlParameterSource[]::new)), "insert intraday bar revision");
        qualityIssues.forEach(issue -> recordQuality(context, issue));
        revisionSupport.completeCursor(fence, context, ordered.getLast().checksum());
        return new RevisionBatchResult(inserted, revised, unchanged, maxRevision);
    }

    @Transactional
    public RevisionBatchResult writeDailyRevision(MarketDataWriteContext context, MarketBarDailyWrite value) {
        return writeDailyBatch(context, List.of(value));
    }

    /**
     * Persists one normalized daily page while holding the dimension cursor lock.
     */
    @Transactional
    public RevisionBatchResult writeDailyBatch(MarketDataWriteContext context,
                                               List<MarketBarDailyWrite> values) {
        Objects.requireNonNull(context, "context");
        List<MarketBarDailyWrite> ordered = validateDailyBatch(values);
        MarketBarDailyWrite dimension = ordered.getFirst();
        MarketRevisionTransactionSupport.RevisionFence fence = revisionSupport.lockCursor(
                dimension.sourceId(), dimension.instrumentId(), MarketRevisionTransactionSupport.DAILY_BAR,
                dimension.priceType(), BarInterval.D1);

        List<SqlParameterSource> closes = new ArrayList<>();
        List<SqlParameterSource> inserts = new ArrayList<>();
        List<RevisionQuality> qualityIssues = new ArrayList<>();
        Map<LocalDate, CurrentRevision> currentRevisions = currentDaily(ordered);
        Instant revisionEffectiveAt = revisionSupport.revisionEffectiveAt(fence, ordered.stream()
                .filter(value -> currentRevisions.containsKey(value.barDate()))
                .filter(value -> !value.checksum().equals(currentRevisions.get(value.barDate()).checksum()))
                .map(value -> currentRevisions.get(value.barDate()).validFrom())
                .toList());
        int inserted = 0;
        int revised = 0;
        int unchanged = 0;
        long maxRevision = 0;
        for (MarketBarDailyWrite value : ordered) {
            Optional<CurrentRevision> current = Optional.ofNullable(currentRevisions.get(value.barDate()));
            if (current.isPresent() && current.orElseThrow().checksum().equals(value.checksum())) {
                unchanged++;
                maxRevision = Math.max(maxRevision, current.orElseThrow().revision());
                continue;
            }
            long nextRevision = current.map(CurrentRevision::revision).orElse(0L) + 1L;
            if (current.isPresent()) {
                CurrentRevision old = current.orElseThrow();
                closes.add(dailyKey(value)
                        .addValue("revision", old.revision())
                        .addValue("checksum", old.checksum())
                        .addValue("validTo", JdbcMarketDataSupport.instantParameter(revisionEffectiveAt)));
                if (old.closed()) {
                    qualityIssues.add(new RevisionQuality(value.sourceId(), value.instrumentId(),
                            MarketRevisionTransactionSupport.DAILY_BAR, value.priceType(), BarInterval.D1,
                            value.barDate().atStartOfDay().toInstant(ZoneOffset.UTC),
                            old.revision(), old.checksum(), nextRevision, value.checksum()));
                }
                revised++;
            } else {
                inserted++;
            }
            inserts.add(dailyParameters(value, nextRevision, revisionEffectiveAt));
            maxRevision = Math.max(maxRevision, nextRevision);
        }

        verifyBatchCounts(jdbcTemplate.batchUpdate("""
                update investment_market_bar_daily
                set revision_slot = revision, valid_to = :validTo
                where source_id = :sourceId and instrument_id = :instrumentId
                  and price_type = :priceType and bar_date = :barDate and revision_slot = 0
                  and revision = :revision and checksum = :checksum
                """, closes.toArray(SqlParameterSource[]::new)), "close daily bar revision");
        verifyBatchCounts(jdbcTemplate.batchUpdate("""
                insert into investment_market_bar_daily (
                    source_id, instrument_id, price_type, bar_date,
                    open_price, high_price, low_price, close_price, base_volume, quote_volume,
                    is_closed, source_updated_at, ingested_at, revision, revision_slot,
                    valid_from, valid_to, checksum
                ) values (
                    :sourceId, :instrumentId, :priceType, :barDate,
                    :openPrice, :highPrice, :lowPrice, :closePrice, :baseVolume, :quoteVolume,
                    :closed, :sourceUpdatedAt, :ingestedAt, :revision, 0,
                    :validFrom, null, :checksum
                )
                """, inserts.toArray(SqlParameterSource[]::new)), "insert daily bar revision");
        qualityIssues.forEach(issue -> recordQuality(context, issue));
        revisionSupport.completeCursor(fence, context, ordered.getLast().checksum());
        return new RevisionBatchResult(inserted, revised, unchanged, maxRevision);
    }

    @Transactional(readOnly = true)
    public List<MarketBarIntradayRow> findCurrentIntraday(long sourceId, long instrumentId,
                                                          PriceType priceType, BarInterval interval,
                                                          Instant fromInclusive, Instant toExclusive,
                                                          int offset, int limit) {
        MapSqlParameterSource parameters = intradayReadParameters(sourceId, instrumentId, priceType, interval,
                fromInclusive, toExclusive);
        JdbcMarketDataSupport.pageParameters(parameters, offset, limit);
        return jdbcTemplate.query("select " + INTRADAY_COLUMNS + """
                from investment_market_bar_intraday
                where source_id = :sourceId and instrument_id = :instrumentId
                  and price_type = :priceType and interval_code = :interval
                  and open_time >= :fromInclusive and open_time < :toExclusive
                  and revision_slot = 0
                order by open_time asc, revision asc
                limit :limit offset :offset
                """, parameters, this::mapIntraday);
    }

    @Transactional(readOnly = true)
    public List<MarketBarIntradayRow> findIntradayAsOf(long sourceId, long instrumentId,
                                                       PriceType priceType, BarInterval interval,
                                                       Instant fromInclusive, Instant toExclusive,
                                                       Instant dataAsOf, int offset, int limit) {
        MapSqlParameterSource parameters = intradayReadParameters(sourceId, instrumentId, priceType, interval,
                fromInclusive, toExclusive).addValue("dataAsOf", JdbcMarketDataSupport.instantParameter(
                        Objects.requireNonNull(dataAsOf, "dataAsOf")));
        JdbcMarketDataSupport.pageParameters(parameters, offset, limit);
        return jdbcTemplate.query("select " + INTRADAY_COLUMNS + """
                from investment_market_bar_intraday
                where source_id = :sourceId and instrument_id = :instrumentId
                  and price_type = :priceType and interval_code = :interval
                  and open_time >= :fromInclusive and open_time < :toExclusive
                  and valid_from <= :dataAsOf
                  and (valid_to is null or valid_to > :dataAsOf)
                order by open_time asc, revision asc
                limit :limit offset :offset
                """, parameters, this::mapIntraday);
    }

    @Transactional(readOnly = true)
    public List<MarketBarDailyRow> findCurrentDaily(long sourceId, long instrumentId, PriceType priceType,
                                                    LocalDate fromInclusive, LocalDate toExclusive,
                                                    int offset, int limit) {
        MapSqlParameterSource parameters = dailyReadParameters(sourceId, instrumentId, priceType,
                fromInclusive, toExclusive);
        JdbcMarketDataSupport.pageParameters(parameters, offset, limit);
        return jdbcTemplate.query("select " + DAILY_COLUMNS + """
                from investment_market_bar_daily
                where source_id = :sourceId and instrument_id = :instrumentId
                  and price_type = :priceType
                  and bar_date >= :fromInclusive and bar_date < :toExclusive
                  and revision_slot = 0
                order by bar_date asc, revision asc
                limit :limit offset :offset
                """, parameters, this::mapDaily);
    }

    @Transactional(readOnly = true)
    public List<MarketBarDailyRow> findDailyAsOf(long sourceId, long instrumentId, PriceType priceType,
                                                 LocalDate fromInclusive, LocalDate toExclusive,
                                                 Instant dataAsOf, int offset, int limit) {
        MapSqlParameterSource parameters = dailyReadParameters(sourceId, instrumentId, priceType,
                fromInclusive, toExclusive).addValue("dataAsOf", JdbcMarketDataSupport.instantParameter(
                        Objects.requireNonNull(dataAsOf, "dataAsOf")));
        JdbcMarketDataSupport.pageParameters(parameters, offset, limit);
        return jdbcTemplate.query("select " + DAILY_COLUMNS + """
                from investment_market_bar_daily
                where source_id = :sourceId and instrument_id = :instrumentId
                  and price_type = :priceType
                  and bar_date >= :fromInclusive and bar_date < :toExclusive
                  and valid_from <= :dataAsOf
                  and (valid_to is null or valid_to > :dataAsOf)
                order by bar_date asc, revision asc
                limit :limit offset :offset
                """, parameters, this::mapDaily);
    }

    private Map<Instant, CurrentRevision> currentIntraday(List<MarketBarIntradayWrite> values) {
        MarketBarIntradayWrite dimension = values.getFirst();
        MapSqlParameterSource parameters = intradayKey(dimension)
                .addValue("openTimes", JdbcMarketDataSupport.instantParameters(
                        values.stream().map(MarketBarIntradayWrite::openTime).toList()));
        Map<Instant, CurrentRevision> revisions = new HashMap<>();
        jdbcTemplate.query("""
                select revision, checksum, valid_from, open_time, is_closed
                from investment_market_bar_intraday
                where source_id = :sourceId and instrument_id = :instrumentId
                  and price_type = :priceType and interval_code = :interval
                  and open_time in (:openTimes) and revision_slot = 0
                """, parameters, (resultSet, rowNumber) -> new TimedRevision(
                JdbcMarketDataSupport.instant(resultSet, "open_time"),
                new CurrentRevision(resultSet.getLong("revision"), resultSet.getString("checksum"),
                        JdbcMarketDataSupport.instant(resultSet, "valid_from"),
                        resultSet.getBoolean("is_closed"))))
                .forEach(value -> revisions.put(value.time(), value.revision()));
        return revisions;
    }

    private Map<LocalDate, CurrentRevision> currentDaily(List<MarketBarDailyWrite> values) {
        MarketBarDailyWrite dimension = values.getFirst();
        MapSqlParameterSource parameters = dailyKey(dimension)
                .addValue("barDates", values.stream().map(MarketBarDailyWrite::barDate).toList());
        Map<LocalDate, CurrentRevision> revisions = new HashMap<>();
        jdbcTemplate.query("""
                select revision, checksum, valid_from, bar_date, is_closed
                from investment_market_bar_daily
                where source_id = :sourceId and instrument_id = :instrumentId
                  and price_type = :priceType and bar_date in (:barDates) and revision_slot = 0
                """, parameters, (resultSet, rowNumber) -> new DatedRevision(
                resultSet.getObject("bar_date", LocalDate.class),
                new CurrentRevision(resultSet.getLong("revision"), resultSet.getString("checksum"),
                        JdbcMarketDataSupport.instant(resultSet, "valid_from"),
                        resultSet.getBoolean("is_closed"))))
                .forEach(value -> revisions.put(value.date(), value.revision()));
        return revisions;
    }

    private List<MarketBarIntradayWrite> validateIntradayBatch(List<MarketBarIntradayWrite> values) {
        JdbcMarketDataSupport.requireSingleDimension(values);
        MarketBarIntradayWrite first = Objects.requireNonNull(values.getFirst(), "value");
        Set<Instant> openTimes = new HashSet<>();
        for (MarketBarIntradayWrite value : values) {
            Objects.requireNonNull(value, "value");
            if (value.sourceId() != first.sourceId() || value.instrumentId() != first.instrumentId()
                    || value.priceType() != first.priceType() || value.interval() != first.interval()) {
                throw new IllegalArgumentException("All intraday bars in a batch must share one cursor dimension");
            }
            if (!openTimes.add(value.openTime())) {
                throw new IllegalArgumentException("Duplicate intraday bar natural key in batch: " + value.openTime());
            }
        }
        return values.stream().sorted(Comparator.comparing(MarketBarIntradayWrite::openTime)).toList();
    }

    private List<MarketBarDailyWrite> validateDailyBatch(List<MarketBarDailyWrite> values) {
        JdbcMarketDataSupport.requireSingleDimension(values);
        MarketBarDailyWrite first = Objects.requireNonNull(values.getFirst(), "value");
        Set<LocalDate> dates = new HashSet<>();
        for (MarketBarDailyWrite value : values) {
            Objects.requireNonNull(value, "value");
            if (value.sourceId() != first.sourceId() || value.instrumentId() != first.instrumentId()
                    || value.priceType() != first.priceType()) {
                throw new IllegalArgumentException("All daily bars in a batch must share one cursor dimension");
            }
            if (!dates.add(value.barDate())) {
                throw new IllegalArgumentException("Duplicate daily bar natural key in batch: " + value.barDate());
            }
        }
        return values.stream().sorted(Comparator.comparing(MarketBarDailyWrite::barDate)).toList();
    }

    private MapSqlParameterSource intradayKey(MarketBarIntradayWrite value) {
        return new MapSqlParameterSource()
                .addValue("sourceId", value.sourceId())
                .addValue("instrumentId", value.instrumentId())
                .addValue("priceType", value.priceType().name())
                .addValue("interval", value.interval().name())
                .addValue("openTime", JdbcMarketDataSupport.instantParameter(value.openTime()));
    }

    private MapSqlParameterSource intradayParameters(MarketBarIntradayWrite value, long revision,
                                                     Instant validFrom) {
        return intradayKey(value)
                .addValue("closeTime", JdbcMarketDataSupport.instantParameter(value.closeTime()))
                .addValue("openPrice", value.openPrice())
                .addValue("highPrice", value.highPrice())
                .addValue("lowPrice", value.lowPrice())
                .addValue("closePrice", value.closePrice())
                .addValue("baseVolume", value.baseVolume())
                .addValue("quoteVolume", value.quoteVolume())
                .addValue("closed", value.closed())
                .addValue("sourceUpdatedAt", JdbcMarketDataSupport.instantParameter(value.sourceUpdatedAt()))
                .addValue("ingestedAt", JdbcMarketDataSupport.instantParameter(value.ingestedAt()))
                .addValue("revision", revision)
                .addValue("validFrom", JdbcMarketDataSupport.instantParameter(validFrom))
                .addValue("checksum", value.checksum());
    }

    private MapSqlParameterSource dailyKey(MarketBarDailyWrite value) {
        return new MapSqlParameterSource()
                .addValue("sourceId", value.sourceId())
                .addValue("instrumentId", value.instrumentId())
                .addValue("priceType", value.priceType().name())
                .addValue("barDate", value.barDate());
    }

    private MapSqlParameterSource dailyParameters(MarketBarDailyWrite value, long revision, Instant validFrom) {
        return dailyKey(value)
                .addValue("openPrice", value.openPrice())
                .addValue("highPrice", value.highPrice())
                .addValue("lowPrice", value.lowPrice())
                .addValue("closePrice", value.closePrice())
                .addValue("baseVolume", value.baseVolume())
                .addValue("quoteVolume", value.quoteVolume())
                .addValue("closed", value.closed())
                .addValue("sourceUpdatedAt", JdbcMarketDataSupport.instantParameter(value.sourceUpdatedAt()))
                .addValue("ingestedAt", JdbcMarketDataSupport.instantParameter(value.ingestedAt()))
                .addValue("revision", revision)
                .addValue("validFrom", JdbcMarketDataSupport.instantParameter(validFrom))
                .addValue("checksum", value.checksum());
    }

    private MapSqlParameterSource intradayReadParameters(long sourceId, long instrumentId, PriceType priceType,
                                                         BarInterval interval, Instant from, Instant to) {
        if (sourceId <= 0 || instrumentId <= 0 || priceType == null || priceType == PriceType.NONE
                || interval == null || interval == BarInterval.NONE) {
            throw new IllegalArgumentException("A concrete intraday dimension is required");
        }
        Objects.requireNonNull(from, "fromInclusive");
        Objects.requireNonNull(to, "toExclusive");
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("toExclusive must be after fromInclusive");
        }
        return new MapSqlParameterSource()
                .addValue("sourceId", sourceId)
                .addValue("instrumentId", instrumentId)
                .addValue("priceType", priceType.name())
                .addValue("interval", interval.name())
                .addValue("fromInclusive", JdbcMarketDataSupport.instantParameter(from))
                .addValue("toExclusive", JdbcMarketDataSupport.instantParameter(to));
    }

    private MapSqlParameterSource dailyReadParameters(long sourceId, long instrumentId, PriceType priceType,
                                                      LocalDate from, LocalDate to) {
        if (sourceId <= 0 || instrumentId <= 0 || priceType == null || priceType == PriceType.NONE) {
            throw new IllegalArgumentException("A concrete daily dimension is required");
        }
        Objects.requireNonNull(from, "fromInclusive");
        Objects.requireNonNull(to, "toExclusive");
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("toExclusive must be after fromInclusive");
        }
        return new MapSqlParameterSource()
                .addValue("sourceId", sourceId)
                .addValue("instrumentId", instrumentId)
                .addValue("priceType", priceType.name())
                .addValue("fromInclusive", from)
                .addValue("toExclusive", to);
    }

    private MarketBarIntradayRow mapIntraday(java.sql.ResultSet resultSet, int rowNumber)
            throws java.sql.SQLException {
        return new MarketBarIntradayRow(
                resultSet.getLong("source_id"), resultSet.getLong("instrument_id"),
                PriceType.valueOf(resultSet.getString("price_type")),
                BarInterval.valueOf(resultSet.getString("interval_code")),
                JdbcMarketDataSupport.instant(resultSet, "open_time"),
                JdbcMarketDataSupport.instant(resultSet, "close_time"),
                resultSet.getBigDecimal("open_price"), resultSet.getBigDecimal("high_price"),
                resultSet.getBigDecimal("low_price"), resultSet.getBigDecimal("close_price"),
                resultSet.getBigDecimal("base_volume"), resultSet.getBigDecimal("quote_volume"),
                resultSet.getBoolean("is_closed"), JdbcMarketDataSupport.instant(resultSet, "source_updated_at"),
                JdbcMarketDataSupport.instant(resultSet, "ingested_at"), resultSet.getLong("revision"),
                JdbcMarketDataSupport.instant(resultSet, "valid_from"),
                JdbcMarketDataSupport.instant(resultSet, "valid_to"), resultSet.getString("checksum"));
    }

    private MarketBarDailyRow mapDaily(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
        return new MarketBarDailyRow(
                resultSet.getLong("source_id"), resultSet.getLong("instrument_id"),
                PriceType.valueOf(resultSet.getString("price_type")),
                resultSet.getObject("bar_date", LocalDate.class),
                resultSet.getBigDecimal("open_price"), resultSet.getBigDecimal("high_price"),
                resultSet.getBigDecimal("low_price"), resultSet.getBigDecimal("close_price"),
                resultSet.getBigDecimal("base_volume"), resultSet.getBigDecimal("quote_volume"),
                resultSet.getBoolean("is_closed"), JdbcMarketDataSupport.instant(resultSet, "source_updated_at"),
                JdbcMarketDataSupport.instant(resultSet, "ingested_at"), resultSet.getLong("revision"),
                JdbcMarketDataSupport.instant(resultSet, "valid_from"),
                JdbcMarketDataSupport.instant(resultSet, "valid_to"), resultSet.getString("checksum"));
    }

    private void recordQuality(MarketDataWriteContext context, RevisionQuality issue) {
        revisionSupport.recordRevision(context, issue.sourceId(), issue.instrumentId(), issue.dataType(),
                issue.priceType(), issue.interval(), issue.pointTime(), issue.oldRevision(), issue.oldChecksum(),
                issue.newRevision(), issue.newChecksum());
    }

    private void verifyBatchCounts(int[] counts, String operation) {
        for (int count : counts) {
            if (count != 1 && count != Statement.SUCCESS_NO_INFO) {
                throw new IllegalStateException("Concurrent market-data write detected during " + operation);
            }
        }
    }

    private record CurrentRevision(long revision, String checksum, Instant validFrom, boolean closed) {
    }

    private record TimedRevision(Instant time, CurrentRevision revision) {
    }

    private record DatedRevision(LocalDate date, CurrentRevision revision) {
    }

    private record RevisionQuality(long sourceId, long instrumentId, String dataType, PriceType priceType,
                                   BarInterval interval, Instant pointTime, long oldRevision, String oldChecksum,
                                   long newRevision, String newChecksum) {
    }
}
