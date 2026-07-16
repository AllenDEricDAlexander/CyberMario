package top.egon.mario.investment.common.job;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.common.utils.LogUtil;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executes slow handler bodies outside database transactions and delegates fenced completion.
 */
@Service
@Slf4j
public class InvestmentJobExecutionService {

    private final InvestmentJobHandlerRegistry handlerRegistry;
    private final InvestmentJobClaimService claimService;
    private final InvestmentJobCompletionService completionService;
    private final InvestmentJobProperties properties;
    private final ScheduledExecutorService heartbeatExecutor;

    public InvestmentJobExecutionService(InvestmentJobHandlerRegistry handlerRegistry,
                                         InvestmentJobClaimService claimService,
                                         InvestmentJobCompletionService completionService,
                                         InvestmentJobProperties properties) {
        this.handlerRegistry = handlerRegistry;
        this.claimService = claimService;
        this.completionService = completionService;
        this.properties = properties;
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "investment-job-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Transactional(propagation = Propagation.NEVER)
    public boolean execute(InvestmentJobClaim claim) {
        AtomicBoolean leaseLost = new AtomicBoolean();
        AtomicBoolean finalizing = new AtomicBoolean();
        Object heartbeatMonitor = new Object();
        ScheduledFuture<?> heartbeat = scheduleHeartbeat(claim, leaseLost, finalizing, heartbeatMonitor);
        try {
            InvestmentJobHandlerResult result = handlerRegistry.require(claim.jobType()).execute(claim);
            stopHeartbeats(finalizing, heartbeatMonitor);
            if (leaseLost.get()) {
                return rejectLeaseLost(claim);
            }
            if (result == null) {
                return transitionAccepted(claim,
                        completionService.fail(claim, "JOB_RESULT_INVALID", "Job handler returned no result"));
            }
            if (result.deferred()) {
                return transitionAccepted(claim, completionService.defer(claim, result.nextAvailableAt()));
            }
            return transitionAccepted(claim, completionService.complete(claim, result.resultJson()));
        } catch (InvestmentJobRetryableException ex) {
            stopHeartbeats(finalizing, heartbeatMonitor);
            if (leaseLost.get()) {
                return rejectLeaseLost(claim);
            }
            return transitionAccepted(claim,
                    completionService.retryWithBackoff(claim, ex.errorCode(), message(ex)));
        } catch (InvestmentJobNonRetryableException ex) {
            stopHeartbeats(finalizing, heartbeatMonitor);
            if (leaseLost.get()) {
                return rejectLeaseLost(claim);
            }
            return transitionAccepted(claim, completionService.fail(claim, ex.errorCode(), message(ex)));
        } catch (RuntimeException ex) {
            LogUtil.warn(log).log("investment job execution failed, jobId={}, jobType={}, error={}",
                    claim.id(), claim.jobType(), message(ex));
            stopHeartbeats(finalizing, heartbeatMonitor);
            if (leaseLost.get()) {
                return rejectLeaseLost(claim);
            }
            return transitionAccepted(claim,
                    completionService.retryWithBackoff(claim, "UNEXPECTED_ERROR", message(ex)));
        } finally {
            stopHeartbeats(finalizing, heartbeatMonitor);
            heartbeat.cancel(false);
        }
    }

    @PreDestroy
    void shutdown() {
        heartbeatExecutor.shutdownNow();
    }

    private ScheduledFuture<?> scheduleHeartbeat(InvestmentJobClaim claim, AtomicBoolean leaseLost,
                                                  AtomicBoolean finalizing, Object heartbeatMonitor) {
        long intervalMillis = Math.max(1L, properties.heartbeatInterval().toMillis());
        return heartbeatExecutor.scheduleWithFixedDelay(
                () -> heartbeat(claim, leaseLost, finalizing, heartbeatMonitor),
                intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    private void heartbeat(InvestmentJobClaim claim, AtomicBoolean leaseLost, AtomicBoolean finalizing,
                           Object heartbeatMonitor) {
        synchronized (heartbeatMonitor) {
            if (finalizing.get()) {
                return;
            }
            try {
                boolean renewed = claimService.heartbeat(claim);
                if (!renewed && leaseLost.compareAndSet(false, true)) {
                    LogUtil.warn(log).log("investment job lease lost, jobId={}, workerId={}",
                            claim.id(), claim.workerId());
                }
            } catch (RuntimeException ex) {
                LogUtil.warn(log).log("investment job heartbeat failed, jobId={}, error={}",
                        claim.id(), message(ex));
            }
        }
    }

    private void stopHeartbeats(AtomicBoolean finalizing, Object heartbeatMonitor) {
        synchronized (heartbeatMonitor) {
            finalizing.set(true);
        }
    }

    private boolean transitionAccepted(InvestmentJobClaim claim, boolean accepted) {
        if (!accepted) {
            rejectLeaseLost(claim);
        }
        return accepted;
    }

    private boolean transitionAccepted(InvestmentJobClaim claim, InvestmentJobTransition transition) {
        return transitionAccepted(claim, transition != InvestmentJobTransition.REJECTED);
    }

    private boolean rejectLeaseLost(InvestmentJobClaim claim) {
        LogUtil.warn(log).log("investment job final transition rejected by lease fence, jobId={}, workerId={}",
                claim.id(), claim.workerId());
        return false;
    }

    private String message(RuntimeException ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
