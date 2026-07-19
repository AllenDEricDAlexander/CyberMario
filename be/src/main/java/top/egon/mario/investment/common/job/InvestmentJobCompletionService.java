package top.egon.mario.investment.common.job;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Central fenced state-transition service for durable jobs.
 */
@Service
public class InvestmentJobCompletionService {

    private static final int MAX_ERROR_CODE_LENGTH = 64;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final InvestmentJobProperties properties;
    private final InvestmentJobJsonSupport jsonSupport;
    private final InvestmentJobLeaseFence leaseFence;
    private final String resultExpression;

    public InvestmentJobCompletionService(NamedParameterJdbcTemplate jdbcTemplate,
                                          InvestmentJobProperties properties,
                                          InvestmentJobJsonSupport jsonSupport,
                                          InvestmentJobLeaseFence leaseFence) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.jsonSupport = jsonSupport;
        this.leaseFence = leaseFence;
        this.resultExpression = jsonSupport.writeExpression("resultJson");
    }

    @Transactional
    public boolean complete(InvestmentJobClaim claim, String resultJson) {
        Assert.notNull(claim, "claim must not be null");
        Assert.hasText(resultJson, "resultJson must not be blank");
        Optional<InvestmentJobLeaseFence.LeaseState> lease = leaseFence.lockValid(claim);
        if (lease.isEmpty()) {
            return false;
        }
        Instant now = lease.orElseThrow().now();
        String sql = """
                update investment_job
                set status = 'SUCCEEDED', result_json = %s,
                    locked_at = null, locked_by = null, claim_token = null,
                    lease_expires_at = null, heartbeat_at = null,
                    last_error_code = null, last_error_message = null,
                    finished_at = :now, updated_at = :now
                where id = :id and status = 'RUNNING'
                  and locked_by = :workerId and claim_token = :claimToken
                """.formatted(resultExpression);
        return jdbcTemplate.update(sql, fenceParameters(claim)
                .addValue("resultJson", jsonSupport.normalize(resultJson, "resultJson"))
                .addValue("now", InvestmentJobJdbcSupport.instantParameter(now))) == 1;
    }

    @Transactional
    public boolean defer(InvestmentJobClaim claim, Instant nextAvailableAt) {
        Assert.notNull(claim, "claim must not be null");
        Assert.notNull(nextAvailableAt, "nextAvailableAt must not be null");
        Optional<InvestmentJobLeaseFence.LeaseState> lease = leaseFence.lockValid(claim);
        if (lease.isEmpty()) {
            return false;
        }
        Instant now = lease.orElseThrow().now();
        return jdbcTemplate.update("""
                        update investment_job
                        set status = 'PENDING', available_at = :nextAvailableAt,
                            locked_at = null, locked_by = null, claim_token = null,
                            lease_expires_at = null, heartbeat_at = null,
                            last_error_code = null, last_error_message = null,
                            finished_at = null, updated_at = :now
                        where id = :id and status = 'RUNNING'
                          and locked_by = :workerId and claim_token = :claimToken
                        """, fenceParameters(claim)
                        .addValue("nextAvailableAt", InvestmentJobJdbcSupport.instantParameter(nextAvailableAt))
                        .addValue("now", InvestmentJobJdbcSupport.instantParameter(now))) == 1;
    }

    @Transactional
    public InvestmentJobTransition retryWithBackoff(InvestmentJobClaim claim,
                                                     String errorCode,
                                                     String errorMessage) {
        Duration delay = exponentialBackoff(claim.attempts() + 1);
        return retry(claim, errorCode, errorMessage, delay);
    }

    @Transactional
    public InvestmentJobTransition retry(InvestmentJobClaim claim,
                                         String errorCode,
                                         String errorMessage,
                                         Duration delay) {
        Assert.notNull(claim, "claim must not be null");
        Assert.isTrue(delay != null && !delay.isNegative(), "delay must not be negative");
        Optional<InvestmentJobLeaseFence.LeaseState> lease = leaseFence.lockValid(claim);
        if (lease.isEmpty()) {
            return InvestmentJobTransition.REJECTED;
        }
        InvestmentJobLeaseFence.LeaseState state = lease.orElseThrow();
        Instant now = state.now();
        int nextAttempts = Math.min(state.attempts() + 1, state.maxAttempts());
        boolean terminal = nextAttempts >= state.maxAttempts();
        int updated = jdbcTemplate.update("""
                        update investment_job
                        set status = :status, attempts = :attempts, available_at = :availableAt,
                            locked_at = null, locked_by = null, claim_token = null,
                            lease_expires_at = null, heartbeat_at = null,
                            last_error_code = :errorCode, last_error_message = :errorMessage,
                            finished_at = :finishedAt, updated_at = :now
                        where id = :id and status = 'RUNNING'
                          and locked_by = :workerId and claim_token = :claimToken
                        """, fenceParameters(claim)
                        .addValue("status", terminal ? "FAILED" : "PENDING")
                        .addValue("attempts", nextAttempts)
                        .addValue("availableAt", InvestmentJobJdbcSupport.instantParameter(
                                terminal ? now : now.plus(delay)))
                        .addValue("errorCode", truncate(errorCode, MAX_ERROR_CODE_LENGTH, "JOB_RETRY"))
                        .addValue("errorMessage", truncate(errorMessage, MAX_ERROR_MESSAGE_LENGTH, "job retry failed"))
                        .addValue("finishedAt", InvestmentJobJdbcSupport.instantParameter(terminal ? now : null))
                        .addValue("now", InvestmentJobJdbcSupport.instantParameter(now)));
        if (updated == 0) {
            return InvestmentJobTransition.REJECTED;
        }
        return terminal ? InvestmentJobTransition.TERMINAL_FAILED : InvestmentJobTransition.RETRY_SCHEDULED;
    }

    @Transactional
    public boolean fail(InvestmentJobClaim claim, String errorCode, String errorMessage) {
        Assert.notNull(claim, "claim must not be null");
        Optional<InvestmentJobLeaseFence.LeaseState> lease = leaseFence.lockValid(claim);
        if (lease.isEmpty()) {
            return false;
        }
        InvestmentJobLeaseFence.LeaseState state = lease.orElseThrow();
        Instant now = state.now();
        int nextAttempts = Math.min(state.attempts() + 1, state.maxAttempts());
        return jdbcTemplate.update("""
                        update investment_job
                        set status = 'FAILED', attempts = :attempts,
                            locked_at = null, locked_by = null, claim_token = null,
                            lease_expires_at = null, heartbeat_at = null,
                            last_error_code = :errorCode, last_error_message = :errorMessage,
                            finished_at = :now, updated_at = :now
                        where id = :id and status = 'RUNNING'
                          and locked_by = :workerId and claim_token = :claimToken
                        """, fenceParameters(claim)
                        .addValue("attempts", nextAttempts)
                        .addValue("errorCode", truncate(errorCode, MAX_ERROR_CODE_LENGTH, "JOB_FAILED"))
                        .addValue("errorMessage", truncate(errorMessage, MAX_ERROR_MESSAGE_LENGTH, "job failed"))
                        .addValue("now", InvestmentJobJdbcSupport.instantParameter(now))) == 1;
    }

    private Duration exponentialBackoff(int attempt) {
        Duration delay = properties.retryBaseDelay();
        for (int index = 1; index < attempt && delay.compareTo(properties.retryMaxDelay()) < 0; index++) {
            if (delay.compareTo(properties.retryMaxDelay().dividedBy(2)) > 0) {
                return properties.retryMaxDelay();
            }
            delay = delay.multipliedBy(2);
        }
        return delay.compareTo(properties.retryMaxDelay()) > 0 ? properties.retryMaxDelay() : delay;
    }

    private MapSqlParameterSource fenceParameters(InvestmentJobClaim claim) {
        return new MapSqlParameterSource()
                .addValue("id", claim.id())
                .addValue("workerId", claim.workerId())
                .addValue("claimToken", claim.claimToken());
    }

    private String truncate(String value, int maxLength, String fallback) {
        String normalized = StringUtils.hasText(value) ? value : fallback;
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
