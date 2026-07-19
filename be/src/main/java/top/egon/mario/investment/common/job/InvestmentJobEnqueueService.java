package top.egon.mario.investment.common.job;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Creates immutable job inputs while collapsing duplicate scheduling attempts.
 */
@Service
public class InvestmentJobEnqueueService {

    private static final String INSERT = """
            insert into investment_job (
                workspace_id, job_type, status, priority, available_at, attempts, max_attempts,
                idempotency_key, input_json, result_json, created_at, updated_at
            ) values (
                :workspaceId, :jobType, 'PENDING', :priority, :availableAt, 0, :maxAttempts,
                :idempotencyKey, %s, %s, :now, :now
            )
            on conflict (idempotency_key) do nothing
            """;

    private static final String PORTABLE_INSERT = """
            insert into investment_job (
                workspace_id, job_type, status, priority, available_at, attempts, max_attempts,
                idempotency_key, input_json, result_json, created_at, updated_at
            )
            select :workspaceId, :jobType, 'PENDING', :priority, :availableAt, 0, :maxAttempts,
                   :idempotencyKey, %s, %s, :now, :now
            where not exists (
                select 1 from investment_job where idempotency_key = :idempotencyKey
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final InvestmentJobJsonSupport jsonSupport;
    private final Clock clock;
    private final String insertSql;

    public InvestmentJobEnqueueService(NamedParameterJdbcTemplate jdbcTemplate,
                                       InvestmentJobJsonSupport jsonSupport,
                                       Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonSupport = jsonSupport;
        this.clock = clock;
        String inputExpression = jsonSupport.writeExpression("inputJson");
        String emptyResultExpression = jsonSupport.writeExpression("emptyResultJson");
        String insertTemplate = jsonSupport.supportsOnConflict() ? INSERT : PORTABLE_INSERT;
        this.insertSql = insertTemplate.formatted(inputExpression, emptyResultExpression);
    }

    @Transactional
    public long enqueue(InvestmentJobEnqueueCommand command) {
        Instant now = clock.instant();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("workspaceId", command.workspaceId())
                .addValue("jobType", command.jobType().name())
                .addValue("priority", command.priority())
                .addValue("availableAt", InvestmentJobJdbcSupport.instantParameter(command.availableAt()))
                .addValue("maxAttempts", command.maxAttempts())
                .addValue("idempotencyKey", command.idempotencyKey())
                .addValue("inputJson", jsonSupport.normalize(command.inputJson(), "inputJson"))
                .addValue("emptyResultJson", "{}")
                .addValue("now", InvestmentJobJdbcSupport.instantParameter(now));
        jdbcTemplate.update(insertSql, parameters);
        return jdbcTemplate.queryForObject("""
                        select id from investment_job where idempotency_key = :idempotencyKey
                        """, Map.of("idempotencyKey", command.idempotencyKey()), Long.class);
    }

    /** Creates a missing job or advances an unlocked pending job after a relevant market commit. */
    @Transactional
    public long enqueueOrWake(InvestmentJobEnqueueCommand command) {
        long id = enqueue(command);
        wakePending(command.idempotencyKey(), command.availableAt());
        return id;
    }

    @Transactional
    public boolean wakePending(String idempotencyKey, Instant availableAt) {
        Instant now = clock.instant();
        return jdbcTemplate.update("""
                update investment_job
                set available_at = :availableAt, updated_at = :now
                where idempotency_key = :idempotencyKey
                  and status = 'PENDING'
                  and locked_by is null
                  and available_at > :availableAt
                """, new MapSqlParameterSource()
                .addValue("idempotencyKey", idempotencyKey)
                .addValue("availableAt", InvestmentJobJdbcSupport.instantParameter(availableAt))
                .addValue("now", InvestmentJobJdbcSupport.instantParameter(now))) == 1;
    }
}
