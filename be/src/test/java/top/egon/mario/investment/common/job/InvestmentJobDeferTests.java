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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class InvestmentJobDeferTests {

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
    void deferReturnsToPendingWithoutAttemptOrFailureMutation() {
        long jobId = enqueue("defer-normal");
        InvestmentJobClaim claim = claimService.claimNext("worker-a").orElseThrow();
        Instant dependencyAvailableAt = Instant.now().plusSeconds(90);

        assertThat(completionService.defer(claim, dependencyAvailableAt)).isTrue();

        InvestmentJobPo deferred = jobRepository.findById(jobId).orElseThrow();
        assertThat(deferred.getStatus()).isEqualTo(InvestmentJobStatus.PENDING);
        assertThat(deferred.getAvailableAt()).isEqualTo(dependencyAvailableAt);
        assertThat(deferred.getAttempts()).isZero();
        assertThat(deferred.getLastErrorCode()).isNull();
        assertThat(deferred.getLastErrorMessage()).isNull();
        assertThat(deferred.getFinishedAt()).isNull();
    }

    @Test
    void deferRequiresCurrentFenceAndIsDistinctFromDependencyRetry() {
        long deferredJobId = enqueue("defer-fence");
        InvestmentJobClaim claim = claimService.claimNext("worker-a").orElseThrow();
        InvestmentJobClaim stale = new InvestmentJobClaim(claim.id(), claim.workspaceId(), claim.jobType(),
                claim.inputJson(), claim.attempts(), claim.maxAttempts(), claim.workerId(), "stale-token",
                claim.claimedAt(), claim.leaseExpiresAt());

        assertThat(completionService.defer(stale, Instant.now().plusSeconds(30))).isFalse();
        assertThat(jobRepository.findById(deferredJobId)).get()
                .extracting(InvestmentJobPo::getStatus)
                .isEqualTo(InvestmentJobStatus.RUNNING);
        assertThat(completionService.defer(claim, Instant.now().plusSeconds(30))).isTrue();

        long retryJobId = enqueue("dependency-retry");
        InvestmentJobClaim retryClaim = claimService.claimNext("worker-b").orElseThrow();
        assertThat(completionService.retryWithBackoff(retryClaim,
                "DEPENDENCY_UNAVAILABLE", "waiting dependency"))
                .isEqualTo(InvestmentJobTransition.RETRY_SCHEDULED);

        assertThat(jobRepository.findById(deferredJobId)).get().satisfies(job -> {
            assertThat(job.getAttempts()).isZero();
            assertThat(job.getLastErrorCode()).isNull();
        });
        assertThat(jobRepository.findById(retryJobId)).get().satisfies(job -> {
            assertThat(job.getAttempts()).isEqualTo(1);
            assertThat(job.getLastErrorCode()).isEqualTo("DEPENDENCY_UNAVAILABLE");
        });
    }

    @Test
    void deferRejectsAnExpiredMatchingFence() {
        long jobId = enqueue("defer-expired");
        InvestmentJobClaim claim = claimService.claimNext("worker-a").orElseThrow();
        jdbcTemplate.update("update investment_job set lease_expires_at = ? where id = ?", Instant.EPOCH, jobId);

        assertThat(completionService.defer(claim, Instant.now().plusSeconds(30))).isFalse();
        assertThat(jobRepository.findById(jobId)).get()
                .extracting(InvestmentJobPo::getStatus)
                .isEqualTo(InvestmentJobStatus.RUNNING);
    }

    private long enqueue(String key) {
        return enqueueService.enqueue(new InvestmentJobEnqueueCommand(null, InvestmentJobType.CONTRACT_SYNC,
                100, Instant.EPOCH, 3, key, "{}"));
    }
}
