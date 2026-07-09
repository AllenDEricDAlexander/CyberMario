package top.egon.mario.clocktower.agent.runtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import top.egon.mario.common.utils.LogUtil;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component("clocktowerAgentTaskWorkerRunner")
@ConditionalOnProperty(prefix = "clocktower.agent.worker", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@ConditionalOnProperty(prefix = "clocktower.agent.worker.runner", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@Slf4j
public class ClocktowerAgentTaskWorkerRunner implements SmartLifecycle {

    private final ClocktowerAgentTaskWorker taskWorker;
    private final ClocktowerAgentWorkerProperties properties;
    private volatile boolean running;
    private ScheduledExecutorService executorService;

    public ClocktowerAgentTaskWorkerRunner(ClocktowerAgentTaskWorker taskWorker,
                                           ClocktowerAgentWorkerProperties properties) {
        this.taskWorker = taskWorker;
        this.properties = properties;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        executorService = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "clocktower-agent-task-worker");
            thread.setDaemon(true);
            return thread;
        });
        executorService.scheduleWithFixedDelay(this::processSafely,
                properties.getWorker().initialDelayMs(), properties.getWorker().fixedDelayMs(),
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
        return Integer.MAX_VALUE - 90;
    }

    private void processSafely() {
        try {
            taskWorker.processBatch("clocktower-agent-task-worker", properties.getWorker().batchSize());
        } catch (RuntimeException ex) {
            LogUtil.warn(log).log("clocktower agent task worker batch failed, error={}", ex.getMessage());
        }
    }
}
