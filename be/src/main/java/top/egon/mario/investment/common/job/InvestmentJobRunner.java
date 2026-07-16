package top.egon.mario.investment.common.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import top.egon.mario.common.utils.LogUtil;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight poll trigger; all durable state remains in PostgreSQL.
 */
@Component("investmentJobRunner")
@ConditionalOnProperty(prefix = "mario.investment.job.runner", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@Slf4j
public class InvestmentJobRunner implements SmartLifecycle {

    private final InvestmentJobWorker worker;
    private final InvestmentJobProperties properties;
    private final String workerId = "investment-job-" + UUID.randomUUID();
    private volatile boolean running;
    private ScheduledExecutorService executorService;

    public InvestmentJobRunner(InvestmentJobWorker worker, InvestmentJobProperties properties) {
        this.worker = worker;
        this.properties = properties;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        executorService = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "investment-job-runner");
            thread.setDaemon(true);
            return thread;
        });
        executorService.scheduleWithFixedDelay(this::processSafely,
                properties.getRunner().initialDelay().toMillis(),
                properties.getRunner().pollInterval().toMillis(), TimeUnit.MILLISECONDS);
        running = true;
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 80;
    }

    private void processSafely() {
        try {
            worker.processBatch(workerId, properties.batchSize());
        } catch (RuntimeException ex) {
            LogUtil.warn(log).log("investment job worker batch failed, error={}",
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }
}
