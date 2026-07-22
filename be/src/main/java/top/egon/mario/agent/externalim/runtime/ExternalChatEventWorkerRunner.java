package top.egon.mario.agent.externalim.runtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import top.egon.mario.common.utils.LogUtil;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(prefix = "mario.agent.external-im.worker", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@Slf4j
public class ExternalChatEventWorkerRunner implements SmartLifecycle {

    private final ExternalChatEventWorker worker;
    private final ExternalChatWorkerProperties properties;
    private final String workerId = "external-im-" + UUID.randomUUID();
    private volatile boolean running;
    private ScheduledExecutorService executorService;

    public ExternalChatEventWorkerRunner(ExternalChatEventWorker worker,
                                         ExternalChatWorkerProperties properties) {
        this.worker = worker;
        this.properties = properties;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        executorService = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "external-im-event-runner");
            thread.setDaemon(true);
            return thread;
        });
        executorService.scheduleWithFixedDelay(this::processSafely,
                properties.initialDelay().toMillis(), properties.pollInterval().toMillis(),
                TimeUnit.MILLISECONDS);
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
            worker.processBatch(workerId);
        } catch (RuntimeException error) {
            LogUtil.warn(log).log("external IM event batch failed, error={}",
                    error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        }
    }
}
