package top.egon.mario.investment.marketdata.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.model.InvestmentJobStatus;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;
import top.egon.mario.investment.marketdata.subscription.MarketSubscription;
import top.egon.mario.investment.marketdata.web.dto.InvestmentDataQualityIssueResponse;
import top.egon.mario.investment.marketdata.web.dto.InvestmentPlatformJobResponse;
import top.egon.mario.investment.marketdata.web.dto.InvestmentPlatformSubscriptionResponse;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Platform operations facade; subscription declarations remain strictly read-only.
 */
@Service
public class InvestmentPlatformQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> PLATFORM_JOB_TYPES = Set.of(
            "CONTRACT_SYNC", "POSITION_TIER_SYNC", "BAR_BACKFILL", "BAR_INCREMENTAL",
            "QUOTE_REFRESH", "FUNDING_RATE_BACKFILL", "FUNDING_RATE_INCREMENTAL", "DATA_QUALITY_CHECK");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final InvestmentMarketSubscriptionRegistry subscriptionRegistry;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    @Autowired
    public InvestmentPlatformQueryService(NamedParameterJdbcTemplate jdbcTemplate,
                                          InvestmentMarketSubscriptionRegistry subscriptionRegistry,
                                          ObjectMapper objectMapper) {
        this(jdbcTemplate, subscriptionRegistry, Clock.systemUTC(), objectMapper);
    }

    public InvestmentPlatformQueryService(NamedParameterJdbcTemplate jdbcTemplate,
                                          InvestmentMarketSubscriptionRegistry subscriptionRegistry,
                                          Clock clock) {
        this(jdbcTemplate, subscriptionRegistry, clock, new ObjectMapper().findAndRegisterModules());
    }

    public InvestmentPlatformQueryService(NamedParameterJdbcTemplate jdbcTemplate,
                                          InvestmentMarketSubscriptionRegistry subscriptionRegistry,
                                          Clock clock,
                                          ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.subscriptionRegistry = Objects.requireNonNull(subscriptionRegistry, "subscriptionRegistry");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public List<InvestmentPlatformSubscriptionResponse> subscriptions() {
        return subscriptionRegistry.subscriptions().stream().map(this::subscription).toList();
    }

    @Transactional(readOnly = true)
    public PageResult<InvestmentPlatformJobResponse> jobs(String status, String jobType, int page, int size) {
        PageWindow window = pageWindow(page, size);
        String normalizedStatus = enumValue(status, InvestmentJobStatus.class, "job status");
        String normalizedType = enumValue(jobType, InvestmentJobType.class, "job type");
        if (normalizedType != null && !PLATFORM_JOB_TYPES.contains(normalizedType)) {
            throw invalid("Unsupported platform job type");
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("limit", window.size()).addValue("offset", window.offset());
        StringBuilder filters = new StringBuilder();
        appendFilter(filters, parameters, "status", normalizedStatus, "and status = :status");
        appendFilter(filters, parameters, "jobType", normalizedType, "and job_type = :jobType");
        long total = count("""
                select count(*) from investment_job
                where workspace_id is null
                  and job_type in (
                    'CONTRACT_SYNC', 'POSITION_TIER_SYNC', 'BAR_BACKFILL', 'BAR_INCREMENTAL',
                    'QUOTE_REFRESH', 'FUNDING_RATE_BACKFILL', 'FUNDING_RATE_INCREMENTAL', 'DATA_QUALITY_CHECK')
                  %s
                """.formatted(filters), parameters);
        List<InvestmentPlatformJobResponse> records = total == 0 ? List.of() : jdbcTemplate.query("""
                select id, job_type, status, priority, attempts, max_attempts, available_at,
                       last_error_code, last_error_message, input_json, result_json,
                       started_at, finished_at, created_at, updated_at
                from investment_job
                where workspace_id is null
                  and job_type in (
                    'CONTRACT_SYNC', 'POSITION_TIER_SYNC', 'BAR_BACKFILL', 'BAR_INCREMENTAL',
                    'QUOTE_REFRESH', 'FUNDING_RATE_BACKFILL', 'FUNDING_RATE_INCREMENTAL', 'DATA_QUALITY_CHECK')
                  %s
                order by created_at desc, id desc
                limit :limit offset :offset
                """.formatted(filters), parameters, this::mapJob);
        return page(records, window, total);
    }

    @Transactional(readOnly = true)
    public PageResult<InvestmentDataQualityIssueResponse> qualityIssues(String resolutionStatus, String severity,
                                                                        int page, int size) {
        PageWindow window = pageWindow(page, size);
        String normalizedResolution = allowed(resolutionStatus, "resolution status",
                Set.of("OPEN", "RESOLVED", "IGNORED"));
        String normalizedSeverity = allowed(severity, "severity", Set.of("INFO", "WARNING", "ERROR"));
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("limit", window.size()).addValue("offset", window.offset());
        StringBuilder filters = new StringBuilder();
        appendFilter(filters, parameters, "resolutionStatus", normalizedResolution,
                "and resolution_status = :resolutionStatus");
        appendFilter(filters, parameters, "severity", normalizedSeverity, "and severity = :severity");
        long total = count("""
                select count(*) from investment_data_quality_issue
                where deleted = false
                  %s
                """.formatted(filters), parameters);
        List<InvestmentDataQualityIssueResponse> records = total == 0 ? List.of() : jdbcTemplate.query("""
                select id, instrument_id, data_type, price_type, interval_code, point_time,
                       issue_code, severity, resolution_status, resolved_at, created_at
                from investment_data_quality_issue
                where deleted = false
                  %s
                order by created_at desc, id desc
                limit :limit offset :offset
                """.formatted(filters), parameters, this::mapQualityIssue);
        return page(records, window, total);
    }

    /**
     * Explicit operator retry; code subscriptions themselves have no write operation.
     */
    @Transactional
    public void retryFailedJob(long jobId) {
        requireId(jobId, "jobId");
        Instant now = clock.instant();
        int updated = jdbcTemplate.update("""
                update investment_job
                set status = 'PENDING', attempts = 0, available_at = :now,
                    locked_at = null, locked_by = null, claim_token = null,
                    lease_expires_at = null, heartbeat_at = null,
                    last_error_code = null, last_error_message = null,
                    started_at = null, finished_at = null, updated_at = :now
                where id = :jobId and status = 'FAILED' and workspace_id is null
                  and job_type in (
                    'CONTRACT_SYNC', 'POSITION_TIER_SYNC', 'BAR_BACKFILL', 'BAR_INCREMENTAL',
                    'QUOTE_REFRESH', 'FUNDING_RATE_BACKFILL', 'FUNDING_RATE_INCREMENTAL', 'DATA_QUALITY_CHECK')
                """, new MapSqlParameterSource("jobId", jobId).addValue("now", Timestamp.from(now)));
        if (updated != 1) {
            throw new InvestmentException(InvestmentErrorCode.CONFLICT,
                    "Only an existing failed Investment job can be retried");
        }
    }

    @Transactional
    public void resolveQualityIssue(long issueId, Long actorId) {
        requireId(issueId, "issueId");
        requireId(actorId == null ? 0 : actorId, "actorId");
        Instant now = clock.instant();
        int updated = jdbcTemplate.update("""
                update investment_data_quality_issue
                set resolution_status = 'RESOLVED', resolved_at = :now, updated_at = :now,
                    updated_by = :actorId, version = version + 1
                where id = :issueId and deleted = false and resolution_status = 'OPEN'
                """, new MapSqlParameterSource("issueId", issueId)
                .addValue("actorId", actorId).addValue("now", Timestamp.from(now)));
        if (updated != 1) {
            throw new InvestmentException(InvestmentErrorCode.CONFLICT,
                    "Only an existing open quality issue can be resolved");
        }
    }

    private InvestmentPlatformSubscriptionResponse subscription(MarketSubscription subscription) {
        return new InvestmentPlatformSubscriptionResponse(
                subscription.sourceCode(), subscription.productType().name(), subscription.symbol(), "CODE_DECLARED",
                names(subscription.capabilities()), names(subscription.priceTypes()), names(subscription.intervals()),
                durationMap(subscription.schedule().refreshIntervals()),
                durationMap(subscription.schedule().backfillWindows()), retention(subscription));
    }

    private Map<String, String> durationMap(Map<? extends Enum<?>, java.time.Duration> durations) {
        Map<String, String> response = new LinkedHashMap<>();
        durations.entrySet().stream().sorted(java.util.Comparator.comparing(entry -> entry.getKey().name()))
                .forEach(entry -> response.put(entry.getKey().name(), entry.getValue().toString()));
        return response;
    }

    private Map<String, String> retention(MarketSubscription subscription) {
        Map<String, String> response = new LinkedHashMap<>();
        subscription.retentionPolicy().permanentIntervals().stream().sorted()
                .forEach(interval -> response.put(interval.name(), "PERMANENT"));
        subscription.retentionPolicy().retainedFor().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> response.put(entry.getKey().name(), entry.getValue().toString()));
        return response;
    }

    private InvestmentPlatformJobResponse mapJob(ResultSet resultSet, int rowNumber) throws SQLException {
        JsonNode input = json(resultSet.getString("input_json"));
        JsonNode result = json(resultSet.getString("result_json"));
        return new InvestmentPlatformJobResponse(
                resultSet.getLong("id"), resultSet.getString("job_type"), resultSet.getString("status"),
                resultSet.getInt("priority"), resultSet.getInt("attempts"), resultSet.getInt("max_attempts"),
                instant(resultSet, "available_at"), resultSet.getString("last_error_code"),
                resultSet.getString("last_error_message"), instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"), text(input, "triggerSource", "SCHEDULED"),
                text(input, "sourceCode", null), text(input, "symbol", null),
                text(input, "capability", null), text(input, "priceType", null),
                text(input, "interval", null), instant(input, "startInclusive"),
                instant(input, "endExclusive"), instant(resultSet, "started_at"),
                instant(resultSet, "finished_at"), integer(result, "fetched"),
                integer(result, "written"));
    }

    private InvestmentDataQualityIssueResponse mapQualityIssue(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new InvestmentDataQualityIssueResponse(
                resultSet.getLong("id"), resultSet.getLong("instrument_id"), resultSet.getString("data_type"),
                resultSet.getString("price_type"), resultSet.getString("interval_code"),
                instant(resultSet, "point_time"), resultSet.getString("issue_code"),
                resultSet.getString("severity"), resultSet.getString("resolution_status"),
                instant(resultSet, "resolved_at"), instant(resultSet, "created_at"));
    }

    private PageWindow pageWindow(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw invalid("Invalid platform pagination");
        }
        long offset = Math.multiplyExact((long) page - 1, size);
        if (offset > Integer.MAX_VALUE) {
            throw invalid("Pagination offset is too large");
        }
        return new PageWindow(page, size, (int) offset);
    }

    private long count(String sql, MapSqlParameterSource parameters) {
        Long value = jdbcTemplate.queryForObject(sql, parameters, Long.class);
        return value == null ? 0 : value;
    }

    private void appendFilter(StringBuilder filters, MapSqlParameterSource parameters,
                              String parameterName, String value, String predicate) {
        if (value != null) {
            parameters.addValue(parameterName, value);
            filters.append(predicate).append('\n');
        }
    }

    private <T> PageResult<T> page(List<T> records, PageWindow window, long total) {
        int pages = Math.toIntExact((total + window.size() - 1) / window.size());
        return new PageResult<>(records, window.page(), window.size(), total, pages);
    }

    private <E extends Enum<E>> String enumValue(String value, Class<E> type, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return Enum.valueOf(type, normalized).name();
        } catch (IllegalArgumentException exception) {
            throw invalid("Unsupported " + field);
        }
    }

    private String allowed(String value, String field, Set<String> allowed) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw invalid("Unsupported " + field);
        }
        return normalized;
    }

    private void requireId(long id, String field) {
        if (id <= 0) {
            throw invalid(field + " must be positive");
        }
    }

    private static <T extends Enum<T>> List<String> names(Collection<T> values) {
        return values.stream().map(Enum::name).sorted().toList();
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return resultSet.getTimestamp(column).toInstant();
    }

    private JsonNode json(String value) {
        if (value == null || value.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            return node != null && node.isObject() ? node : objectMapper.createObjectNode();
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isTextual() ? fallback : value.textValue();
    }

    private Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.canConvertToInt() ? null : value.intValue();
    }

    private Instant instant(JsonNode node, String field) {
        String value = text(node, field, null);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static InvestmentException invalid(String message) {
        return new InvestmentException(InvestmentErrorCode.INVALID_REQUEST, message);
    }

    private record PageWindow(int page, int size, int offset) {
    }
}
