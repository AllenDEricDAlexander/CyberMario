package top.egon.mario.investment.marketdata.repository.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.po.InvestmentDataQualityIssuePo;
import top.egon.mario.investment.marketdata.po.InvestmentIngestCursorPo;
import top.egon.mario.investment.marketdata.repository.InvestmentDataQualityIssueRepository;
import top.egon.mario.investment.marketdata.repository.InvestmentIngestCursorRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketDataWriteContext;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Shared JPA seam for cursor fencing and quality facts around JDBC revision writes.
 */
@Component
class MarketRevisionTransactionSupport {

    static final String DAILY_BAR = "BAR_DAILY";
    static final String INTRADAY_BAR = "BAR_INTRADAY";
    static final String FUNDING_RATE = "FUNDING_RATE";

    private final InvestmentIngestCursorRepository cursorRepository;
    private final InvestmentDataQualityIssueRepository qualityIssueRepository;
    private final ObjectMapper objectMapper;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Clock fallbackClock;

    MarketRevisionTransactionSupport(InvestmentIngestCursorRepository cursorRepository,
                                     InvestmentDataQualityIssueRepository qualityIssueRepository,
                                     ObjectMapper objectMapper,
                                     NamedParameterJdbcTemplate jdbcTemplate,
                                     ObjectProvider<Clock> clockProvider) {
        this.cursorRepository = cursorRepository;
        this.qualityIssueRepository = qualityIssueRepository;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.fallbackClock = clockProvider.getIfAvailable(Clock::systemUTC);
    }

    RevisionFence lockCursor(long sourceId, long instrumentId, String dataType,
                             PriceType priceType, BarInterval interval) {
        InvestmentIngestCursorPo cursor = cursorRepository
                .findDimensionForUpdate(sourceId, instrumentId, dataType, priceType, interval)
                .orElseThrow(() -> new IllegalStateException("Ingestion cursor does not exist for "
                        + dataType + ":" + sourceId + ":" + instrumentId + ":" + priceType + ":" + interval));
        return new RevisionFence(cursor, authoritativeTimeAfterLock());
    }

    Instant revisionEffectiveAt(RevisionFence fence, Collection<Instant> currentValidFroms) {
        Instant latestValidFrom = currentValidFroms.stream()
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
        if (latestValidFrom != null && !fence.authoritativeAt().isAfter(latestValidFrom)) {
            // PostgreSQL persists timestamptz with microsecond precision, so use its smallest safe successor.
            return latestValidFrom.plus(1, ChronoUnit.MICROS);
        }
        return fence.authoritativeAt();
    }

    void completeCursor(RevisionFence fence, MarketDataWriteContext context, String checksum) {
        InvestmentIngestCursorPo cursor = fence.cursor();
        Instant requestedNextStart = context.nextStartTime();
        if (requestedNextStart != null
                && (cursor.getNextStartTime() == null || requestedNextStart.isAfter(cursor.getNextStartTime()))) {
            cursor.setNextStartTime(requestedNextStart);
        }
        cursor.setLastSuccessTime(fence.authoritativeAt());
        cursor.setLastChecksum(checksum);
        cursor.setStatus("SUCCEEDED");
        cursor.setLastError(null);
        cursor.setUpdatedAt(fence.authoritativeAt());
    }

    void recordRevision(MarketDataWriteContext context, long sourceId, long instrumentId,
                        String dataType, PriceType priceType, BarInterval interval, Instant pointTime,
                        long oldRevision, String oldChecksum, long newRevision, String newChecksum) {
        InvestmentDataQualityIssuePo issue = new InvestmentDataQualityIssuePo();
        issue.setJobId(context.jobId());
        issue.setSourceId(sourceId);
        issue.setInstrumentId(instrumentId);
        issue.setDataType(dataType);
        issue.setPriceType(priceType);
        issue.setInterval(interval);
        issue.setPointTime(pointTime);
        issue.setIssueCode("UNEXPECTED_REVISION");
        issue.setSeverity("WARNING");
        issue.setDetailsJson(json(Map.of(
                "oldRevision", oldRevision,
                "oldChecksum", oldChecksum,
                "newRevision", newRevision,
                "newChecksum", newChecksum
        )));
        issue.setResolutionStatus("OPEN");
        qualityIssueRepository.save(issue);
    }

    private Instant authoritativeTimeAfterLock() {
        Boolean postgresql = jdbcTemplate.getJdbcTemplate().execute((ConnectionCallback<Boolean>) connection ->
                "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName()));
        if (Boolean.TRUE.equals(postgresql)) {
            return jdbcTemplate.queryForObject("select clock_timestamp() as authoritative_at", Map.of(),
                    (resultSet, rowNumber) -> JdbcMarketDataSupport.instant(resultSet, "authoritative_at"));
        }
        return fallbackClock.instant();
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to encode market-data revision details", ex);
        }
    }

    record RevisionFence(InvestmentIngestCursorPo cursor, Instant authoritativeAt) {

        RevisionFence {
            Objects.requireNonNull(cursor, "cursor");
            Objects.requireNonNull(authoritativeAt, "authoritativeAt");
        }
    }
}
