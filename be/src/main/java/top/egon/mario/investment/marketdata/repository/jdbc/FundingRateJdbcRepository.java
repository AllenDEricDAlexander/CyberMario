package top.egon.mario.investment.marketdata.repository.jdbc;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.po.InvestmentIngestCursorPo;
import top.egon.mario.investment.marketdata.repository.jdbc.model.FundingRateRow;
import top.egon.mario.investment.marketdata.repository.jdbc.model.FundingRateWrite;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketDataWriteContext;
import top.egon.mario.investment.marketdata.repository.jdbc.model.RevisionBatchResult;

import java.sql.Statement;
import java.time.Instant;
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
 * JDBC persistence adapter for revisioned historical funding rates.
 */
@Repository
public class FundingRateJdbcRepository {

    private static final String COLUMNS = """
            source_id, instrument_id, funding_time, funding_rate, ingested_at,
            revision, valid_from, valid_to, checksum
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MarketRevisionTransactionSupport revisionSupport;

    public FundingRateJdbcRepository(NamedParameterJdbcTemplate jdbcTemplate,
                                     MarketRevisionTransactionSupport revisionSupport) {
        this.jdbcTemplate = jdbcTemplate;
        this.revisionSupport = revisionSupport;
    }

    @Transactional
    public RevisionBatchResult writeRevision(MarketDataWriteContext context, FundingRateWrite value) {
        return writeBatch(context, List.of(value));
    }

    /**
     * Persists one funding page while holding its sentinel cursor dimension.
     */
    @Transactional
    public RevisionBatchResult writeBatch(MarketDataWriteContext context, List<FundingRateWrite> values) {
        Objects.requireNonNull(context, "context");
        List<FundingRateWrite> ordered = validateBatch(values);
        FundingRateWrite dimension = ordered.getFirst();
        InvestmentIngestCursorPo cursor = revisionSupport.lockCursor(
                dimension.sourceId(), dimension.instrumentId(), MarketRevisionTransactionSupport.FUNDING_RATE,
                PriceType.NONE, BarInterval.NONE);

        List<SqlParameterSource> closes = new ArrayList<>();
        List<SqlParameterSource> inserts = new ArrayList<>();
        List<RevisionQuality> qualityIssues = new ArrayList<>();
        Map<Instant, CurrentRevision> currentRevisions = current(ordered);
        int inserted = 0;
        int revised = 0;
        int unchanged = 0;
        long maxRevision = 0;
        for (FundingRateWrite value : ordered) {
            Optional<CurrentRevision> current = Optional.ofNullable(currentRevisions.get(value.fundingTime()));
            if (current.isPresent() && current.orElseThrow().checksum().equals(value.checksum())) {
                unchanged++;
                maxRevision = Math.max(maxRevision, current.orElseThrow().revision());
                continue;
            }
            long nextRevision = current.map(CurrentRevision::revision).orElse(0L) + 1L;
            if (current.isPresent()) {
                CurrentRevision old = current.orElseThrow();
                if (!context.effectiveAt().isAfter(old.validFrom())) {
                    throw new IllegalArgumentException("A revision effectiveAt must be after the current validFrom");
                }
                closes.add(key(value)
                        .addValue("revision", old.revision())
                        .addValue("checksum", old.checksum())
                        .addValue("validTo", JdbcMarketDataSupport.instantParameter(context.effectiveAt())));
                qualityIssues.add(new RevisionQuality(value.fundingTime(), old.revision(), old.checksum(),
                        nextRevision, value.checksum()));
                revised++;
            } else {
                inserted++;
            }
            inserts.add(parameters(value, nextRevision, context.effectiveAt()));
            maxRevision = Math.max(maxRevision, nextRevision);
        }

        verifyBatchCounts(jdbcTemplate.batchUpdate("""
                update investment_funding_rate
                set revision_slot = revision, valid_to = :validTo
                where source_id = :sourceId and instrument_id = :instrumentId
                  and funding_time = :fundingTime and revision_slot = 0
                  and revision = :revision and checksum = :checksum
                """, closes.toArray(SqlParameterSource[]::new)), "close funding-rate revision");
        verifyBatchCounts(jdbcTemplate.batchUpdate("""
                insert into investment_funding_rate (
                    source_id, instrument_id, funding_time, funding_rate, ingested_at,
                    revision, revision_slot, valid_from, valid_to, checksum
                ) values (
                    :sourceId, :instrumentId, :fundingTime, :fundingRate, :ingestedAt,
                    :revision, 0, :validFrom, null, :checksum
                )
                """, inserts.toArray(SqlParameterSource[]::new)), "insert funding-rate revision");
        qualityIssues.forEach(issue -> revisionSupport.recordRevision(context,
                dimension.sourceId(), dimension.instrumentId(), MarketRevisionTransactionSupport.FUNDING_RATE,
                PriceType.NONE, BarInterval.NONE, issue.pointTime(), issue.oldRevision(), issue.oldChecksum(),
                issue.newRevision(), issue.newChecksum()));
        revisionSupport.completeCursor(cursor, context, ordered.getLast().checksum());
        return new RevisionBatchResult(inserted, revised, unchanged, maxRevision);
    }

    @Transactional(readOnly = true)
    public List<FundingRateRow> findCurrent(long sourceId, long instrumentId,
                                            Instant fromInclusive, Instant toExclusive,
                                            int offset, int limit) {
        MapSqlParameterSource parameters = readParameters(sourceId, instrumentId, fromInclusive, toExclusive);
        JdbcMarketDataSupport.pageParameters(parameters, offset, limit);
        return jdbcTemplate.query("select " + COLUMNS + """
                from investment_funding_rate
                where source_id = :sourceId and instrument_id = :instrumentId
                  and funding_time >= :fromInclusive and funding_time < :toExclusive
                  and revision_slot = 0
                order by funding_time asc, revision asc
                limit :limit offset :offset
                """, parameters, this::mapRow);
    }

    @Transactional(readOnly = true)
    public List<FundingRateRow> findAsOf(long sourceId, long instrumentId,
                                         Instant fromInclusive, Instant toExclusive, Instant dataAsOf,
                                         int offset, int limit) {
        MapSqlParameterSource parameters = readParameters(sourceId, instrumentId, fromInclusive, toExclusive)
                .addValue("dataAsOf", JdbcMarketDataSupport.instantParameter(
                        Objects.requireNonNull(dataAsOf, "dataAsOf")));
        JdbcMarketDataSupport.pageParameters(parameters, offset, limit);
        return jdbcTemplate.query("select " + COLUMNS + """
                from investment_funding_rate
                where source_id = :sourceId and instrument_id = :instrumentId
                  and funding_time >= :fromInclusive and funding_time < :toExclusive
                  and valid_from <= :dataAsOf
                  and (valid_to is null or valid_to > :dataAsOf)
                order by funding_time asc, revision asc
                limit :limit offset :offset
                """, parameters, this::mapRow);
    }

    private Map<Instant, CurrentRevision> current(List<FundingRateWrite> values) {
        FundingRateWrite dimension = values.getFirst();
        MapSqlParameterSource parameters = key(dimension)
                .addValue("fundingTimes", JdbcMarketDataSupport.instantParameters(
                        values.stream().map(FundingRateWrite::fundingTime).toList()));
        Map<Instant, CurrentRevision> revisions = new HashMap<>();
        jdbcTemplate.query("""
                select revision, checksum, valid_from, funding_time
                from investment_funding_rate
                where source_id = :sourceId and instrument_id = :instrumentId
                  and funding_time in (:fundingTimes) and revision_slot = 0
                """, parameters, (resultSet, rowNumber) -> new TimedRevision(
                JdbcMarketDataSupport.instant(resultSet, "funding_time"),
                new CurrentRevision(resultSet.getLong("revision"), resultSet.getString("checksum"),
                        JdbcMarketDataSupport.instant(resultSet, "valid_from"))))
                .forEach(value -> revisions.put(value.time(), value.revision()));
        return revisions;
    }

    private List<FundingRateWrite> validateBatch(List<FundingRateWrite> values) {
        JdbcMarketDataSupport.requireSingleDimension(values);
        FundingRateWrite first = Objects.requireNonNull(values.getFirst(), "value");
        Set<Instant> fundingTimes = new HashSet<>();
        for (FundingRateWrite value : values) {
            Objects.requireNonNull(value, "value");
            if (value.sourceId() != first.sourceId() || value.instrumentId() != first.instrumentId()) {
                throw new IllegalArgumentException("All funding rates in a batch must share one cursor dimension");
            }
            if (!fundingTimes.add(value.fundingTime())) {
                throw new IllegalArgumentException("Duplicate funding-rate natural key in batch: "
                        + value.fundingTime());
            }
        }
        return values.stream().sorted(Comparator.comparing(FundingRateWrite::fundingTime)).toList();
    }

    private MapSqlParameterSource key(FundingRateWrite value) {
        return new MapSqlParameterSource()
                .addValue("sourceId", value.sourceId())
                .addValue("instrumentId", value.instrumentId())
                .addValue("fundingTime", JdbcMarketDataSupport.instantParameter(value.fundingTime()));
    }

    private MapSqlParameterSource parameters(FundingRateWrite value, long revision, Instant validFrom) {
        return key(value)
                .addValue("fundingRate", value.fundingRate())
                .addValue("ingestedAt", JdbcMarketDataSupport.instantParameter(value.ingestedAt()))
                .addValue("revision", revision)
                .addValue("validFrom", JdbcMarketDataSupport.instantParameter(validFrom))
                .addValue("checksum", value.checksum());
    }

    private MapSqlParameterSource readParameters(long sourceId, long instrumentId, Instant from, Instant to) {
        if (sourceId <= 0 || instrumentId <= 0) {
            throw new IllegalArgumentException("sourceId and instrumentId must be positive");
        }
        Objects.requireNonNull(from, "fromInclusive");
        Objects.requireNonNull(to, "toExclusive");
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("toExclusive must be after fromInclusive");
        }
        return new MapSqlParameterSource()
                .addValue("sourceId", sourceId)
                .addValue("instrumentId", instrumentId)
                .addValue("fromInclusive", JdbcMarketDataSupport.instantParameter(from))
                .addValue("toExclusive", JdbcMarketDataSupport.instantParameter(to));
    }

    private FundingRateRow mapRow(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
        return new FundingRateRow(
                resultSet.getLong("source_id"), resultSet.getLong("instrument_id"),
                JdbcMarketDataSupport.instant(resultSet, "funding_time"),
                resultSet.getBigDecimal("funding_rate"),
                JdbcMarketDataSupport.instant(resultSet, "ingested_at"), resultSet.getLong("revision"),
                JdbcMarketDataSupport.instant(resultSet, "valid_from"),
                JdbcMarketDataSupport.instant(resultSet, "valid_to"), resultSet.getString("checksum"));
    }

    private void verifyBatchCounts(int[] counts, String operation) {
        for (int count : counts) {
            if (count != 1 && count != Statement.SUCCESS_NO_INFO) {
                throw new IllegalStateException("Concurrent market-data write detected during " + operation);
            }
        }
    }

    private record CurrentRevision(long revision, String checksum, Instant validFrom) {
    }

    private record TimedRevision(Instant time, CurrentRevision revision) {
    }

    private record RevisionQuality(Instant pointTime, long oldRevision, String oldChecksum,
                                   long newRevision, String newChecksum) {
    }
}
