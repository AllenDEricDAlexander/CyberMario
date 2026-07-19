package top.egon.mario.investment.common.job;

import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import top.egon.mario.investment.common.model.InvestmentJobType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Owns short claim and heartbeat transactions for durable jobs.
 */
@Service
public class InvestmentJobClaimService {

    private static final String PENDING_CANDIDATE = """
            select id
            from investment_job
            where status = 'PENDING' and available_at <= %s
            order by available_at asc, priority asc, id asc
            limit 1
            for update%s
            """;

    private static final String EXPIRED_CANDIDATE = """
            select id
            from investment_job
            where status = 'RUNNING' and lease_expires_at <= %s
              and attempts + 1 < max_attempts
            order by lease_expires_at asc, id asc
            limit 1
            for update%s
            """;

    private static final String EXHAUSTED_CANDIDATES = """
            select id
            from investment_job
            where status = 'RUNNING' and lease_expires_at <= %s
              and attempts + 1 >= max_attempts
            order by lease_expires_at asc, id asc
            limit :limit
            for update%s
            """;

    private static final String CLAIM_PENDING = """
            update investment_job
            set status = 'RUNNING', locked_at = :now, locked_by = :workerId,
                claim_token = :claimToken, lease_expires_at = :leaseExpiresAt,
                heartbeat_at = :now, started_at = coalesce(started_at, :now),
                finished_at = null, updated_at = :now
            where id = :id and status = 'PENDING' and available_at <= :now
            """;

    private static final String CLAIM_EXPIRED = """
            update investment_job
            set locked_at = :now, locked_by = :workerId,
                claim_token = :claimToken, lease_expires_at = :leaseExpiresAt,
                heartbeat_at = :now, attempts = attempts + 1,
                last_error_code = 'JOB_LEASE_EXPIRED',
                last_error_message = 'Previous worker lease expired',
                finished_at = null, updated_at = :now
            where id = :id and status = 'RUNNING' and lease_expires_at <= :now
              and attempts + 1 < max_attempts
            """;

    private static final String FAIL_EXHAUSTED_LEASES = """
            update investment_job
            set status = 'FAILED', attempts = max_attempts,
                locked_at = null, locked_by = null, claim_token = null,
                lease_expires_at = null, heartbeat_at = null,
                last_error_code = 'JOB_LEASE_EXHAUSTED',
                last_error_message = 'Worker lease expired after retry budget was exhausted',
                finished_at = :now, updated_at = :now
            where id in (:ids) and status = 'RUNNING' and lease_expires_at <= :now
              and attempts + 1 >= max_attempts
            """;

    private static final String CLAIM_READ = """
            select id, workspace_id, job_type, input_json, attempts, max_attempts,
                   locked_by, claim_token, locked_at, lease_expires_at
            from investment_job
            where id = :id and status = 'RUNNING'
              and locked_by = :workerId and claim_token = :claimToken
              and lease_expires_at > :now
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final InvestmentJobProperties properties;
    private final InvestmentJobClaimTokenSource claimTokenSource;
    private final InvestmentJobLeaseFence leaseFence;
    private final boolean skipLocked;
    private final String candidateTimeExpression;

    public InvestmentJobClaimService(NamedParameterJdbcTemplate jdbcTemplate,
                                     InvestmentJobProperties properties,
                                     InvestmentJobClaimTokenSource claimTokenSource,
                                     InvestmentJobJsonSupport jsonSupport,
                                     InvestmentJobLeaseFence leaseFence) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.claimTokenSource = claimTokenSource;
        this.leaseFence = leaseFence;
        this.skipLocked = jsonSupport.supportsSkipLocked();
        this.candidateTimeExpression = leaseFence.candidateTimeExpression();
    }

    /**
     * Claims exactly one job so a serial worker never leases work it has not started.
     */
    @Transactional
    public Optional<InvestmentJobClaim> claimNext(String workerId) {
        Assert.isTrue(StringUtils.hasText(workerId), "workerId must not be blank");
        Assert.isTrue(workerId.length() <= 128, "workerId must not exceed 128 characters");
        failExhaustedLeases();

        Long candidateId = findCandidate(EXPIRED_CANDIDATE);
        boolean recoveringExpiredLease = candidateId != null;
        if (candidateId == null) {
            candidateId = findCandidate(PENDING_CANDIDATE);
        }
        if (candidateId == null) {
            return Optional.empty();
        }

        String claimToken = claimTokenSource.nextToken();
        Assert.isTrue(StringUtils.hasText(claimToken), "claimToken must not be blank");
        Assert.isTrue(claimToken.length() <= 64, "claimToken must not exceed 64 characters");
        Instant now = leaseFence.authoritativeNow();
        MapSqlParameterSource parameters = fenceParameters(candidateId, workerId, claimToken)
                .addValue("now", InvestmentJobJdbcSupport.instantParameter(now))
                .addValue("leaseExpiresAt",
                        InvestmentJobJdbcSupport.instantParameter(now.plus(properties.leaseDuration())));
        String claimSql = recoveringExpiredLease ? CLAIM_EXPIRED : CLAIM_PENDING;
        if (jdbcTemplate.update(claimSql, parameters) != 1) {
            return Optional.empty();
        }
        return Optional.ofNullable(jdbcTemplate.query(CLAIM_READ, parameters, claimExtractor()));
    }

    @Transactional
    public boolean heartbeat(InvestmentJobClaim claim) {
        return heartbeat(claim, properties.leaseDuration());
    }

    @Transactional
    public boolean heartbeat(InvestmentJobClaim claim, Duration leaseDuration) {
        Assert.notNull(claim, "claim must not be null");
        Assert.isTrue(leaseDuration != null && !leaseDuration.isNegative() && !leaseDuration.isZero(),
                "leaseDuration must be positive");
        Optional<InvestmentJobLeaseFence.LeaseState> lease = leaseFence.lockValid(claim);
        if (lease.isEmpty()) {
            return false;
        }
        Instant now = lease.orElseThrow().now();
        return jdbcTemplate.update("""
                        update investment_job
                        set heartbeat_at = :now, lease_expires_at = :leaseExpiresAt, updated_at = :now
                        where id = :id and status = 'RUNNING'
                          and locked_by = :workerId and claim_token = :claimToken
                        """, fenceParameters(claim.id(), claim.workerId(), claim.claimToken())
                        .addValue("now", InvestmentJobJdbcSupport.instantParameter(now))
                        .addValue("leaseExpiresAt",
                                InvestmentJobJdbcSupport.instantParameter(now.plus(leaseDuration)))) == 1;
    }

    private void failExhaustedLeases() {
        String candidateSql = EXHAUSTED_CANDIDATES.formatted(
                candidateTimeExpression, skipLocked ? " skip locked" : "");
        Map<String, ?> candidateParameters = candidateParameters();
        List<Long> candidateIds = jdbcTemplate.queryForList(candidateSql,
                new MapSqlParameterSource(candidateParameters).addValue("limit", properties.batchSize()), Long.class);
        if (!candidateIds.isEmpty()) {
            Instant now = leaseFence.authoritativeNow();
            jdbcTemplate.update(FAIL_EXHAUSTED_LEASES, new MapSqlParameterSource()
                    .addValue("ids", candidateIds)
                    .addValue("now", InvestmentJobJdbcSupport.instantParameter(now)));
        }
    }

    private Long findCandidate(String sql) {
        List<Long> candidateIds = jdbcTemplate.queryForList(
                sql.formatted(candidateTimeExpression, skipLocked ? " skip locked" : ""),
                candidateParameters(), Long.class);
        return candidateIds.isEmpty() ? null : candidateIds.getFirst();
    }

    private Map<String, ?> candidateParameters() {
        return leaseFence.usesDatabaseClock()
                ? Map.of()
                : Map.of("now", InvestmentJobJdbcSupport.instantParameter(leaseFence.authoritativeNow()));
    }

    private MapSqlParameterSource fenceParameters(long id, String workerId, String claimToken) {
        return new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("workerId", workerId)
                .addValue("claimToken", claimToken);
    }

    private ResultSetExtractor<InvestmentJobClaim> claimExtractor() {
        return resultSet -> resultSet.next() ? mapClaim(resultSet) : null;
    }

    private InvestmentJobClaim mapClaim(ResultSet resultSet) throws SQLException {
        return new InvestmentJobClaim(
                resultSet.getLong("id"),
                nullableLong(resultSet, "workspace_id"),
                InvestmentJobType.valueOf(resultSet.getString("job_type")),
                resultSet.getString("input_json"),
                resultSet.getInt("attempts"),
                resultSet.getInt("max_attempts"),
                resultSet.getString("locked_by"),
                resultSet.getString("claim_token"),
                instant(resultSet, "locked_at"),
                instant(resultSet, "lease_expires_at")
        );
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private Instant instant(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new SQLException("Unsupported timestamp value for " + column + ": " + value);
    }
}
