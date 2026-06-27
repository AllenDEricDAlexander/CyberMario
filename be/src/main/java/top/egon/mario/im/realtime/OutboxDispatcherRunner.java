package top.egon.mario.im.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import top.egon.mario.common.utils.LogUtil;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component("outboxDispatcherRunner")
@ConditionalOnProperty(prefix = "im.realtime.dispatcher", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "im.realtime.dispatcher.runner", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@Slf4j
public class OutboxDispatcherRunner implements SmartLifecycle {

    private final OutboxDispatcher outboxDispatcher;
    private final int batchSize;
    private final long initialDelayMillis;
    private final long fixedDelayMillis;
    private volatile boolean running;
    private ScheduledExecutorService executorService;

    public OutboxDispatcherRunner(OutboxDispatcher outboxDispatcher,
                                  @Value("${im.realtime.dispatcher.runner.batch-size:100}") int batchSize,
                                  @Value("${im.realtime.dispatcher.runner.initial-delay-ms:1000}") long initialDelayMillis,
                                  @Value("${im.realtime.dispatcher.runner.fixed-delay-ms:1000}") long fixedDelayMillis) {
        this.outboxDispatcher = outboxDispatcher;
        this.batchSize = Math.max(1, batchSize);
        this.initialDelayMillis = Math.max(0L, initialDelayMillis);
        this.fixedDelayMillis = Math.max(1L, fixedDelayMillis);
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        executorService = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "im-outbox-dispatcher");
            thread.setDaemon(true);
            return thread;
        });
        executorService.scheduleWithFixedDelay(this::dispatchSafely, initialDelayMillis, fixedDelayMillis,
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
        return Integer.MAX_VALUE - 100;
    }

    private void dispatchSafely() {
        try {
            outboxDispatcher.dispatchBatch(batchSize);
        } catch (RuntimeException ex) {
            LogUtil.warn(log).log("im outbox dispatcher batch failed, error={}", ex.getMessage());
        }
    }
}
