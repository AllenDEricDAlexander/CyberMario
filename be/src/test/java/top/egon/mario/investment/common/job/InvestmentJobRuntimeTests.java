package top.egon.mario.investment.common.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.mario.investment.common.job.po.InvestmentJobPo;
import top.egon.mario.investment.common.job.repository.InvestmentJobRepository;
import top.egon.mario.investment.common.model.InvestmentJobStatus;
import top.egon.mario.investment.common.model.InvestmentJobType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = InvestmentJobRuntimeTests.DeterministicRuntimeConfiguration.class)
class InvestmentJobRuntimeTests {

    private static final Instant TEST_NOW = Instant.parse("2030-01-02T03:04:05Z");

    @Autowired
    private InvestmentJobEnqueueService enqueueService;

    @Autowired
    private InvestmentJobClaimService claimService;

    @Autowired
    private InvestmentJobCompletionService completionService;

    @Autowired
    private InvestmentJobProperties properties;

    @Autowired
    private InvestmentJobRepository jobRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MutableInvestmentJobClock mutableClock;

    @BeforeEach
    void setUp() {
        mutableClock.set(TEST_NOW);
        jobRepository.deleteAll();
    }

    @Test
    void enqueueIsIdempotentAndPreservesTheFirstImmutableInput() {
        Instant availableAt = TEST_NOW.minusSeconds(1);
        InvestmentJobEnqueueCommand first = command("runtime-idempotent", "{\"symbol\":\"BTCUSDT\"}", availableAt, 3);
        InvestmentJobEnqueueCommand duplicate = command("runtime-idempotent", "{\"symbol\":\"ETHUSDT\"}", availableAt, 5);

        long firstId = enqueueService.enqueue(first);
        long duplicateId = enqueueService.enqueue(duplicate);

        assertThat(duplicateId).isEqualTo(firstId);
        assertThat(jobRepository.findAll()).singleElement().satisfies(job -> {
            assertJsonEquivalent(job.getInputJson(), "{\"symbol\":\"BTCUSDT\"}");
            assertThat(job.getMaxAttempts()).isEqualTo(3);
        });
    }

    @Test
    void objectArrayAndPrimitiveJsonValuesRoundTripWithoutStringEncoding() {
        List<String> values = List.of(
                "{\"symbol\":\"BTCUSDT\"}",
                "[1,\"two\",true,null]",
                "\"text\"",
                "42",
                "true",
                "null"
        );

        for (int index = 0; index < values.size(); index++) {
            String value = values.get(index);
            long jobId = enqueueService.enqueue(command("runtime-json-" + index, value, Instant.EPOCH, 3));
            InvestmentJobClaim claim = claimService.claimNext("json-worker-" + index).orElseThrow();

            assertThat(claim.id()).isEqualTo(jobId);
            assertJsonEquivalent(claim.inputJson(), value);
            assertThat(completionService.complete(claim, value)).isTrue();
            assertThat(jobRepository.findById(jobId)).get().satisfies(job -> {
                assertJsonEquivalent(job.getInputJson(), value);
                assertJsonEquivalent(job.getResultJson(), value);
            });
        }
    }

    @Test
    void enqueueAndCompletionRejectMalformedOrMultipleJsonValues() {
        assertThatThrownBy(() -> enqueueService.enqueue(
                command("runtime-invalid-json", "{broken", Instant.EPOCH, 3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inputJson");
        assertThatThrownBy(() -> enqueueService.enqueue(
                command("runtime-trailing-json", "{} []", Instant.EPOCH, 3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inputJson");

        long jobId = enqueueService.enqueue(command("runtime-invalid-result", "{}", Instant.EPOCH, 3));
        InvestmentJobClaim claim = claimService.claimNext("json-worker").orElseThrow();
        assertThat(claim.id()).isEqualTo(jobId);
        assertThatThrownBy(() -> completionService.complete(claim, "{broken"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resultJson");
        assertThat(jobRepository.findById(jobId)).get()
                .extracting(InvestmentJobPo::getStatus)
                .isEqualTo(InvestmentJobStatus.RUNNING);
    }

    @Test
    void claimIssuesFencingTokenAndPreventsOverlappingClaim() {
        long jobId = enqueueService.enqueue(command("runtime-claim", "{}", Instant.EPOCH, 3));

        Optional<InvestmentJobClaim> first = claimService.claimNext("worker-a");
        Optional<InvestmentJobClaim> overlapping = claimService.claimNext("worker-b");

        assertThat(first).get().satisfies(claim -> {
            assertThat(claim.id()).isEqualTo(jobId);
            assertThat(claim.workerId()).isEqualTo("worker-a");
            assertThat(claim.claimToken()).startsWith("claim-token-");
            assertThat(claim.leaseExpiresAt()).isAfter(claim.claimedAt());
        });
        assertThat(overlapping).isEmpty();
        InvestmentJobClaim firstClaim = first.orElseThrow();
        assertThat(jobRepository.findById(jobId)).get().satisfies(job -> {
            assertThat(job.getStatus()).isEqualTo(InvestmentJobStatus.RUNNING);
            assertThat(job.getLockedBy()).isEqualTo("worker-a");
            assertThat(job.getClaimToken()).isEqualTo(firstClaim.claimToken());
        });
    }

    @Test
    void heartbeatExtendsLeaseAndExpiredLeaseCanBeRecoveredWithNewFence() {
        long jobId = enqueueService.enqueue(command("runtime-recovery", "{}", Instant.EPOCH, 3));
        InvestmentJobClaim staleClaim = claimService.claimNext("worker-a").orElseThrow();
        Instant heartbeatBefore = jobRepository.findById(jobId).orElseThrow().getHeartbeatAt();

        assertThat(claimService.heartbeat(staleClaim, Duration.ofMinutes(2))).isTrue();
        InvestmentJobPo heartbeatJob = jobRepository.findById(jobId).orElseThrow();
        assertThat(heartbeatJob.getHeartbeatAt()).isAfterOrEqualTo(heartbeatBefore);
        assertThat(heartbeatJob.getLeaseExpiresAt()).isAfter(heartbeatJob.getHeartbeatAt());

        jdbcTemplate.update("update investment_job set lease_expires_at = ? where id = ?",
                Instant.EPOCH, jobId);
        InvestmentJobClaim recoveredClaim = claimService.claimNext("worker-b").orElseThrow();

        assertThat(recoveredClaim.claimToken()).isNotEqualTo(staleClaim.claimToken());
        assertThat(recoveredClaim.workerId()).isEqualTo("worker-b");
        assertThat(completionService.complete(staleClaim, "{\"stale\":true}")).isFalse();
        assertThat(completionService.complete(recoveredClaim, "{\"ok\":true}")).isTrue();
        assertThat(jobRepository.findById(jobId)).get().satisfies(job -> {
            assertThat(job.getStatus()).isEqualTo(InvestmentJobStatus.SUCCEEDED);
            assertJsonEquivalent(job.getResultJson(), "{\"ok\":true}");
            assertThat(job.getLockedBy()).isNull();
            assertThat(job.getClaimToken()).isNull();
        });
    }

    @Test
    void expiredLeaseConsumesRetryBudgetAndCannotBeReclaimedAfterExhaustion() {
        long jobId = enqueueService.enqueue(command("runtime-lease-budget", "{}", Instant.EPOCH, 2));
        InvestmentJobClaim firstClaim = claimService.claimNext("worker-a").orElseThrow();
        expireLease(jobId);

        InvestmentJobClaim recoveredClaim = claimService.claimNext("worker-b").orElseThrow();
        assertThat(recoveredClaim.attempts()).isEqualTo(1);
        assertThat(recoveredClaim.claimToken()).isNotEqualTo(firstClaim.claimToken());
        expireLease(jobId);

        assertThat(claimService.claimNext("worker-c")).isEmpty();
        assertThat(jobRepository.findById(jobId)).get().satisfies(job -> {
            assertThat(job.getStatus()).isEqualTo(InvestmentJobStatus.FAILED);
            assertThat(job.getAttempts()).isEqualTo(2);
            assertThat(job.getLastErrorCode()).isEqualTo("JOB_LEASE_EXHAUSTED");
            assertThat(job.getFinishedAt()).isEqualTo(TEST_NOW);
        });
    }

    @Test
    void expiredLeaseRejectsHeartbeatAndCompletionBeforeRecovery() {
        long jobId = enqueueService.enqueue(command("runtime-expired-fence", "{}", Instant.EPOCH, 3));
        InvestmentJobClaim expiredClaim = claimService.claimNext("worker-a").orElseThrow();
        expireLease(jobId);

        assertThat(claimService.heartbeat(expiredClaim)).isFalse();
        assertThat(completionService.complete(expiredClaim, "{\"late\":true}")).isFalse();
        assertThat(jobRepository.findById(jobId)).get()
                .extracting(InvestmentJobPo::getStatus)
                .isEqualTo(InvestmentJobStatus.RUNNING);
    }

    @Test
    void heartbeatUsesPostLockTimeAndRejectsLeaseThatExpiresWhileWaiting() throws Exception {
        long jobId = enqueueService.enqueue(command("runtime-heartbeat-lock-time", "{}", Instant.EPOCH, 3));
        InvestmentJobClaim claim = claimService.claimNext("worker-a").orElseThrow();

        boolean renewed = runAfterRowLockWait(jobId, () -> claimService.heartbeat(claim));

        assertThat(renewed).isFalse();
        assertThat(jobRepository.findById(jobId)).get().satisfies(job -> {
            assertThat(job.getStatus()).isEqualTo(InvestmentJobStatus.RUNNING);
            assertThat(job.getLeaseExpiresAt()).isEqualTo(claim.leaseExpiresAt());
        });
    }

    @Test
    void completionUsesPostLockTimeAndRejectsLeaseThatExpiresWhileWaiting() throws Exception {
        long jobId = enqueueService.enqueue(command("runtime-completion-lock-time", "{}", Instant.EPOCH, 3));
        InvestmentJobClaim claim = claimService.claimNext("worker-a").orElseThrow();

        boolean completed = runAfterRowLockWait(
                jobId, () -> completionService.complete(claim, "{\"late\":true}"));

        assertThat(completed).isFalse();
        assertThat(jobRepository.findById(jobId)).get().satisfies(job -> {
            assertThat(job.getStatus()).isEqualTo(InvestmentJobStatus.RUNNING);
            assertJsonEquivalent(job.getResultJson(), "{}");
        });
    }

    @Test
    void claimStartsLeaseFromPostCandidateLockTime() throws Exception {
        long jobId = enqueueService.enqueue(command("runtime-claim-lock-time", "{}", Instant.EPOCH, 3));

        Optional<InvestmentJobClaim> claim = runAfterRowLockWait(
                jobId, () -> claimService.claimNext("worker-a"));

        Instant expectedStart = TEST_NOW.plus(properties.leaseDuration()).plusSeconds(1);
        assertThat(claim).get().satisfies(value -> {
            assertThat(value.claimedAt()).isEqualTo(expectedStart);
            assertThat(value.leaseExpiresAt()).isEqualTo(expectedStart.plus(properties.leaseDuration()));
        });
    }

    @Test
    void exhaustedLeaseReaperProcessesOnlyOneBoundedBatch() {
        int total = properties.batchSize() + 2;
        for (int index = 0; index < total; index++) {
            enqueueService.enqueue(command("runtime-reaper-" + index, "{}", Instant.EPOCH, 1));
        }
        jdbcTemplate.update("""
                update investment_job
                set status = 'RUNNING', locked_at = ?, locked_by = 'dead-worker',
                    claim_token = 'dead-token', lease_expires_at = ?, heartbeat_at = ?
                where status = 'PENDING'
                """, TEST_NOW.minusSeconds(60), Instant.EPOCH, TEST_NOW.minusSeconds(60));

        assertThat(claimService.claimNext("reaper-worker")).isEmpty();
        assertThat(jobRepository.findAll())
                .filteredOn(job -> job.getStatus() == InvestmentJobStatus.FAILED)
                .hasSize(properties.batchSize())
                .allSatisfy(job -> assertThat(job.getLastErrorCode()).isEqualTo("JOB_LEASE_EXHAUSTED"));
        assertThat(jobRepository.findAll())
                .filteredOn(job -> job.getStatus() == InvestmentJobStatus.RUNNING)
                .hasSize(2);
    }

    @Test
    void postgresqlCandidatesUseStableStatementTimeWhileLeaseFencesUseLiveDatabaseTime() {
        NamedParameterJdbcTemplate namedJdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcTemplate plainJdbcTemplate = mock(JdbcTemplate.class);
        DataSourceProperties dataSourceProperties = new DataSourceProperties();
        dataSourceProperties.setUrl("jdbc:postgresql://localhost/investment");
        InvestmentJobJsonSupport jsonSupport = new InvestmentJobJsonSupport(objectMapper, dataSourceProperties);
        InvestmentJobLeaseFence leaseFence = new InvestmentJobLeaseFence(
                namedJdbcTemplate, jsonSupport, mutableClock);
        Instant databaseNow = TEST_NOW.plusSeconds(17);
        when(namedJdbcTemplate.getJdbcTemplate()).thenReturn(plainJdbcTemplate);
        when(plainJdbcTemplate.queryForObject(eq("select clock_timestamp()"), any(RowMapper.class)))
                .thenReturn(databaseNow);

        assertThat(leaseFence.candidateTimeExpression()).isEqualTo("statement_timestamp()");
        assertThat(leaseFence.authoritativeNow()).isEqualTo(databaseNow);
    }

    @Test
    void durableJobInstantsUseThePostgresqlSupportedJdbcType() {
        assertThat(InvestmentJobJdbcSupport.instantParameter(TEST_NOW))
                .isEqualTo(OffsetDateTime.ofInstant(TEST_NOW, ZoneOffset.UTC));
        assertThat(InvestmentJobJdbcSupport.instantParameter(null)).isNull();
    }

    private InvestmentJobEnqueueCommand command(String idempotencyKey, String inputJson,
                                                Instant availableAt, int maxAttempts) {
        return new InvestmentJobEnqueueCommand(null, InvestmentJobType.CONTRACT_SYNC, 100,
                availableAt, maxAttempts, idempotencyKey, inputJson);
    }

    private void expireLease(long jobId) {
        jdbcTemplate.update("update investment_job set lease_expires_at = ? where id = ?", Instant.EPOCH, jobId);
    }

    private <T> T runAfterRowLockWait(long jobId, Callable<T> operation) throws Exception {
        CountDownLatch rowLocked = new CountDownLatch(1);
        CountDownLatch releaseRow = new CountDownLatch(1);
        CountDownLatch operationStarted = new CountDownLatch(1);
        AtomicReference<Thread> operationThread = new AtomicReference<>();
        ExecutorService lockExecutor = Executors.newSingleThreadExecutor();
        ExecutorService operationExecutor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "investment-job-lock-time-test");
            operationThread.set(thread);
            return thread;
        });
        Future<?> lockHolder = lockExecutor.submit(() ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    jdbcTemplate.queryForObject(
                            "select id from investment_job where id = ? for update", Long.class, jobId);
                    rowLocked.countDown();
                    await(releaseRow);
                }));
        try {
            assertThat(rowLocked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<T> result = operationExecutor.submit(() -> {
                operationStarted.countDown();
                return operation.call();
            });
            assertThat(operationStarted.await(5, TimeUnit.SECONDS)).isTrue();
            awaitDatabaseLockWait(operationThread.get());
            mutableClock.set(TEST_NOW.plus(properties.leaseDuration()).plusSeconds(1));
            releaseRow.countDown();
            lockHolder.get(5, TimeUnit.SECONDS);
            return result.get(5, TimeUnit.SECONDS);
        } finally {
            releaseRow.countDown();
            lockExecutor.shutdownNow();
            operationExecutor.shutdownNow();
        }
    }

    private void awaitDatabaseLockWait(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread.State state = thread.getState();
            boolean inDatabaseLock = Arrays.stream(thread.getStackTrace())
                    .map(StackTraceElement::getClassName)
                    .anyMatch(className -> className.startsWith("org.h2."));
            if (inDatabaseLock && (state == Thread.State.BLOCKED
                    || state == Thread.State.WAITING
                    || state == Thread.State.TIMED_WAITING)) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("Operation did not block on the durable-job row lock");
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while holding the durable-job row lock", ex);
        }
    }

    private void assertJsonEquivalent(String actual, String expected) {
        assertThat(readJson(actual)).isEqualTo(readJson(expected));
    }

    private Object readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new AssertionError("Invalid JSON persisted by durable job runtime: " + json, ex);
        }
    }

    @TestConfiguration
    static class DeterministicRuntimeConfiguration {

        @Bean
        @Primary
        MutableInvestmentJobClock deterministicInvestmentJobClock() {
            return new MutableInvestmentJobClock(TEST_NOW);
        }

        @Bean
        @Primary
        InvestmentJobClaimTokenSource deterministicInvestmentJobClaimTokenSource() {
            AtomicInteger sequence = new AtomicInteger();
            return () -> "claim-token-" + sequence.incrementAndGet();
        }
    }

    static final class MutableInvestmentJobClock extends Clock {

        private final AtomicReference<Instant> now;

        private MutableInvestmentJobClock(Instant initialTime) {
            this.now = new AtomicReference<>(initialTime);
        }

        void set(Instant time) {
            now.set(time);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
