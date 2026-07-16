package top.egon.mario.investment.common.job;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Locks and validates the current durable-job lease against an authoritative post-lock time.
 */
@Component
class InvestmentJobLeaseFence {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final boolean databaseClock;

    InvestmentJobLeaseFence(NamedParameterJdbcTemplate jdbcTemplate,
                            InvestmentJobJsonSupport jsonSupport,
                            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.databaseClock = jsonSupport.supportsDatabaseClock();
    }

    Optional<LeaseState> lockValid(InvestmentJobClaim claim) {
        List<LockedLease> leases = jdbcTemplate.query("""
                        select lease_expires_at, attempts, max_attempts
                        from investment_job
                        where id = :id and status = 'RUNNING'
                          and locked_by = :workerId and claim_token = :claimToken
                        for update
                        """, Map.of(
                        "id", claim.id(),
                        "workerId", claim.workerId(),
                        "claimToken", claim.claimToken()),
                (resultSet, rowNum) -> new LockedLease(
                        instant(resultSet.getObject("lease_expires_at")),
                        resultSet.getInt("attempts"),
                        resultSet.getInt("max_attempts")));
        if (leases.isEmpty()) {
            return Optional.empty();
        }
        Instant now = authoritativeNow();
        LockedLease lease = leases.getFirst();
        if (lease.leaseExpiresAt() == null || !lease.leaseExpiresAt().isAfter(now)) {
            return Optional.empty();
        }
        return Optional.of(new LeaseState(now, lease.attempts(), lease.maxAttempts()));
    }

    Instant authoritativeNow() {
        if (!databaseClock) {
            return clock.instant();
        }
        return jdbcTemplate.getJdbcTemplate().queryForObject(
                "select clock_timestamp()",
                (resultSet, rowNum) -> instant(resultSet.getObject(1)));
    }

    String candidateTimeExpression() {
        return databaseClock ? "statement_timestamp()" : ":now";
    }

    boolean usesDatabaseClock() {
        return databaseClock;
    }

    private Instant instant(Object value) throws SQLException {
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
        throw new SQLException("Unsupported investment job timestamp value: " + value);
    }

    record LeaseState(Instant now, int attempts, int maxAttempts) {
    }

    private record LockedLease(Instant leaseExpiresAt, int attempts, int maxAttempts) {
    }
}
