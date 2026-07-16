package top.egon.mario.investment.common.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockReset;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.mario.investment.common.job.repository.InvestmentJobRepository;
import top.egon.mario.investment.common.model.InvestmentJobStatus;
import top.egon.mario.investment.common.model.InvestmentJobType;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(classes = InvestmentJobTransactionBoundaryTests.HandlerConfiguration.class,
        properties = "mario.investment.job.heartbeat-interval=PT0.01S")
class InvestmentJobTransactionBoundaryTests {

    @Autowired
    private InvestmentJobEnqueueService enqueueService;

    @Autowired
    private InvestmentJobWorker worker;

    @Autowired
    private InvestmentJobRepository jobRepository;

    @MockitoSpyBean(reset = MockReset.AFTER)
    private InvestmentJobClaimService claimService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecordingHandler recordingHandler;

    @Autowired
    private ApplicationContext applicationContext;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
        recordingHandler.reset();
    }

    @Test
    void claimExecutionAndCompletionAreSeparateBeansAndHandlerRunsOutsideTransaction() {
        long jobId = enqueueService.enqueue(new InvestmentJobEnqueueCommand(null,
                InvestmentJobType.CONTRACT_SYNC, 100, Instant.EPOCH, 3,
                "transaction-boundary", "{}"));

        assertThat(applicationContext.getBean(InvestmentJobClaimService.class))
                .isNotSameAs(applicationContext.getBean(InvestmentJobExecutionService.class));
        assertThat(applicationContext.getBean(InvestmentJobExecutionService.class))
                .isNotSameAs(applicationContext.getBean(InvestmentJobCompletionService.class));

        assertThat(worker.processBatch("boundary-worker", 1)).isEqualTo(1);

        assertThat(recordingHandler.transactionActive()).isFalse();
        assertThat(recordingHandler.sawCommittedRunningClaim()).isTrue();
        assertThat(jobRepository.findById(jobId)).get()
                .extracting(job -> job.getStatus())
                .isEqualTo(InvestmentJobStatus.SUCCEEDED);
    }

    @Test
    void testProfileDisablesAutomaticRunner() {
        assertThat(applicationContext.containsBean("investmentJobRunner")).isFalse();
    }

    @Test
    void workerClaimsEachJobOnlyWhenItsHandlerIsReadyToStart() throws Exception {
        long firstJobId = enqueueService.enqueue(new InvestmentJobEnqueueCommand(null,
                InvestmentJobType.CONTRACT_SYNC, 100, Instant.EPOCH, 3,
                "claim-just-in-time-first", "{}"));
        long secondJobId = enqueueService.enqueue(new InvestmentJobEnqueueCommand(null,
                InvestmentJobType.CONTRACT_SYNC, 100, Instant.EPOCH, 3,
                "claim-just-in-time-second", "{}"));
        recordingHandler.block(firstJobId);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<Integer> processed = executor.submit(() -> worker.processBatch("serial-worker", 2));
            assertThat(recordingHandler.awaitBlocked()).isTrue();

            assertThat(jobRepository.findById(firstJobId)).get()
                    .extracting(job -> job.getStatus())
                    .isEqualTo(InvestmentJobStatus.RUNNING);
            assertThat(jobRepository.findById(secondJobId)).get()
                    .extracting(job -> job.getStatus())
                    .isEqualTo(InvestmentJobStatus.PENDING);

            recordingHandler.releaseBlockedJob();
            assertThat(processed.get(5, TimeUnit.SECONDS)).isEqualTo(2);
        } finally {
            recordingHandler.releaseBlockedJob();
            executor.shutdownNow();
        }
    }

    @Test
    void executionRejectsFinalTransitionAfterHeartbeatObservesLeaseLoss() throws Exception {
        long jobId = enqueueService.enqueue(new InvestmentJobEnqueueCommand(null,
                InvestmentJobType.CONTRACT_SYNC, 100, Instant.EPOCH, 3,
                "execution-lease-lost", "{}"));
        recordingHandler.block(jobId);
        CountDownLatch heartbeatRejected = new CountDownLatch(1);
        doAnswer(invocation -> recordRejectedHeartbeat(invocation, heartbeatRejected))
                .when(claimService).heartbeat(any(InvestmentJobClaim.class));
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<Integer> processed = executor.submit(() -> worker.processBatch("lease-lost-worker", 1));
            assertThat(recordingHandler.awaitBlocked()).isTrue();
            jdbcTemplate.update("update investment_job set lease_expires_at = ? where id = ?", Instant.EPOCH, jobId);
            assertThat(heartbeatRejected.await(5, TimeUnit.SECONDS)).isTrue();

            recordingHandler.releaseBlockedJob();
            assertThat(processed.get(5, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(jobRepository.findById(jobId)).get()
                    .extracting(job -> job.getStatus())
                    .isEqualTo(InvestmentJobStatus.RUNNING);
        } finally {
            recordingHandler.releaseBlockedJob();
            executor.shutdownNow();
        }
    }

    private boolean recordRejectedHeartbeat(InvocationOnMock invocation, CountDownLatch heartbeatRejected)
            throws Throwable {
        boolean accepted = (boolean) invocation.callRealMethod();
        if (!accepted) {
            heartbeatRejected.countDown();
        }
        return accepted;
    }

    @TestConfiguration
    static class HandlerConfiguration {

        @Bean
        RecordingHandler recordingHandler(InvestmentJobRepository jobRepository) {
            return new RecordingHandler(jobRepository);
        }
    }

    static final class RecordingHandler implements InvestmentJobHandler {

        private final InvestmentJobRepository jobRepository;
        private final AtomicBoolean transactionActive = new AtomicBoolean();
        private final AtomicBoolean sawCommittedRunningClaim = new AtomicBoolean();
        private volatile long blockedJobId = -1;
        private volatile CountDownLatch handlerEntered = new CountDownLatch(0);
        private volatile CountDownLatch handlerRelease = new CountDownLatch(0);

        private RecordingHandler(InvestmentJobRepository jobRepository) {
            this.jobRepository = jobRepository;
        }

        @Override
        public InvestmentJobType jobType() {
            return InvestmentJobType.CONTRACT_SYNC;
        }

        @Override
        public InvestmentJobHandlerResult execute(InvestmentJobClaim claim) {
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            sawCommittedRunningClaim.set(jobRepository.findById(claim.id())
                    .map(job -> job.getStatus() == InvestmentJobStatus.RUNNING)
                    .orElse(false));
            if (claim.id() == blockedJobId) {
                handlerEntered.countDown();
                try {
                    if (!handlerRelease.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release test job handler");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Test job handler was interrupted", ex);
                }
            }
            return InvestmentJobHandlerResult.completed("{\"handled\":true}");
        }

        void block(long jobId) {
            blockedJobId = jobId;
            handlerEntered = new CountDownLatch(1);
            handlerRelease = new CountDownLatch(1);
        }

        boolean awaitBlocked() throws InterruptedException {
            return handlerEntered.await(5, TimeUnit.SECONDS);
        }

        void releaseBlockedJob() {
            handlerRelease.countDown();
        }

        boolean transactionActive() {
            return transactionActive.get();
        }

        boolean sawCommittedRunningClaim() {
            return sawCommittedRunningClaim.get();
        }

        void reset() {
            handlerRelease.countDown();
            blockedJobId = -1;
            handlerEntered = new CountDownLatch(0);
            handlerRelease = new CountDownLatch(0);
            transactionActive.set(false);
            sawCommittedRunningClaim.set(false);
        }
    }
}
