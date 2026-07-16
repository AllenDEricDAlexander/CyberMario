package top.egon.mario.investment.common.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import top.egon.mario.investment.common.job.po.InvestmentJobPo;
import top.egon.mario.investment.common.job.repository.InvestmentJobRepository;
import top.egon.mario.investment.common.model.InvestmentJobStatus;
import top.egon.mario.investment.common.model.InvestmentJobType;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class InvestmentJobRetryTests {

    @Autowired
    private InvestmentJobEnqueueService enqueueService;

    @Autowired
    private InvestmentJobClaimService claimService;

    @Autowired
    private InvestmentJobCompletionService completionService;

    @Autowired
    private InvestmentJobRepository jobRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
    }

    @Test
    void retryUsesExponentialBackoffAndEventuallyFailsAtMaxAttempts() {
        long jobId = enqueue("retry-terminal", 3);
        Duration firstDelay = Duration.ofSeconds(2);
        InvestmentJobClaim firstClaim = claim(jobId, "worker-a");
        Instant firstRetriedAt = Instant.now();

        assertThat(completionService.retry(firstClaim, "DEPENDENCY_TIMEOUT", "provider timeout", firstDelay))
                .isEqualTo(InvestmentJobTransition.RETRY_SCHEDULED);
        InvestmentJobPo firstRetry = jobRepository.findById(jobId).orElseThrow();
        assertThat(firstRetry.getStatus()).isEqualTo(InvestmentJobStatus.PENDING);
        assertThat(firstRetry.getAttempts()).isEqualTo(1);
        assertThat(firstRetry.getAvailableAt()).isAfterOrEqualTo(firstRetriedAt.plus(firstDelay).minusMillis(250));

        makeAvailable(jobId);
        InvestmentJobClaim secondClaim = claim(jobId, "worker-b");
        assertThat(completionService.retryWithBackoff(secondClaim, "DEPENDENCY_TIMEOUT", "provider timeout"))
                .isEqualTo(InvestmentJobTransition.RETRY_SCHEDULED);
        InvestmentJobPo secondRetry = jobRepository.findById(jobId).orElseThrow();
        assertThat(secondRetry.getAttempts()).isEqualTo(2);
        assertThat(secondRetry.getAvailableAt()).isAfter(Instant.now());

        makeAvailable(jobId);
        InvestmentJobClaim finalClaim = claim(jobId, "worker-c");
        assertThat(completionService.retryWithBackoff(finalClaim, "DEPENDENCY_TIMEOUT", "provider timeout"))
                .isEqualTo(InvestmentJobTransition.TERMINAL_FAILED);
        assertThat(jobRepository.findById(jobId)).get().satisfies(job -> {
            assertThat(job.getStatus()).isEqualTo(InvestmentJobStatus.FAILED);
            assertThat(job.getAttempts()).isEqualTo(3);
            assertThat(job.getFinishedAt()).isNotNull();
            assertThat(job.getLastErrorCode()).isEqualTo("DEPENDENCY_TIMEOUT");
        });
    }

    @Test
    void nonRetryableFailureTerminatesImmediatelyAndRejectsStaleFence() {
        long jobId = enqueue("failure-terminal", 5);
        InvestmentJobClaim claim = claim(jobId, "worker-a");
        InvestmentJobClaim wrongFence = new InvestmentJobClaim(claim.id(), claim.workspaceId(), claim.jobType(),
                claim.inputJson(), claim.attempts(), claim.maxAttempts(), claim.workerId(), "stale-token",
                claim.claimedAt(), claim.leaseExpiresAt());

        assertThat(completionService.fail(wrongFence, "INVALID_INPUT", "bad input")).isFalse();
        assertThat(completionService.fail(claim, "INVALID_INPUT", "bad input")).isTrue();
        assertThat(jobRepository.findById(jobId)).get().satisfies(job -> {
            assertThat(job.getStatus()).isEqualTo(InvestmentJobStatus.FAILED);
            assertThat(job.getAttempts()).isEqualTo(1);
            assertThat(job.getLastErrorCode()).isEqualTo("INVALID_INPUT");
        });
    }

    @Test
    void retryRejectsAnExpiredMatchingFence() {
        long retryJobId = enqueue("expired-retry", 5);
        InvestmentJobClaim retryClaim = claim(retryJobId, "worker-a");
        expireLease(retryJobId);

        assertThat(completionService.retry(retryClaim, "LATE_RETRY", "lease expired", Duration.ZERO))
                .isEqualTo(InvestmentJobTransition.REJECTED);
        assertThat(jobRepository.findById(retryJobId)).get()
                .extracting(InvestmentJobPo::getStatus)
                .isEqualTo(InvestmentJobStatus.RUNNING);
    }

    @Test
    void failureRejectsAnExpiredMatchingFence() {
        long failureJobId = enqueue("expired-failure", 5);
        InvestmentJobClaim failureClaim = claim(failureJobId, "worker-b");
        expireLease(failureJobId);

        assertThat(completionService.fail(failureClaim, "LATE_FAILURE", "lease expired")).isFalse();
        assertThat(jobRepository.findById(failureJobId)).get()
                .extracting(InvestmentJobPo::getStatus)
                .isEqualTo(InvestmentJobStatus.RUNNING);
    }

    private long enqueue(String key, int maxAttempts) {
        return enqueueService.enqueue(new InvestmentJobEnqueueCommand(null, InvestmentJobType.CONTRACT_SYNC,
                100, Instant.EPOCH, maxAttempts, key, "{}"));
    }

    private InvestmentJobClaim claim(long jobId, String workerId) {
        InvestmentJobClaim claim = claimService.claimNext(workerId).orElseThrow();
        assertThat(claim.id()).isEqualTo(jobId);
        return claim;
    }

    private void makeAvailable(long jobId) {
        jdbcTemplate.update("update investment_job set available_at = ? where id = ?", Instant.EPOCH, jobId);
    }

    private void expireLease(long jobId) {
        jdbcTemplate.update("update investment_job set lease_expires_at = ? where id = ?", Instant.EPOCH, jobId);
    }
}
