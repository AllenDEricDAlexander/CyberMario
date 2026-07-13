package top.egon.mario.nutrition.service.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import top.egon.mario.common.utils.LogUtil;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component("nutritionAiRecommendationRunner")
@ConditionalOnProperty(prefix = "mario.nutrition.ai.recommendation.runner", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@Slf4j
public class NutritionAiRecommendationRunner implements SmartLifecycle {

    private final NutritionAiRecommendationScheduler scheduler;
    private final NutritionAiService aiService;
    private final long initialDelayMillis;
    private final long fixedDelayMillis;
    private volatile boolean running;
    private ScheduledExecutorService executorService;

    @Autowired
    public NutritionAiRecommendationRunner(NutritionAiRecommendationScheduler scheduler,
                                           NutritionAiService aiService,
                                           @Value("${mario.nutrition.ai.recommendation.runner.initial-delay-ms:60000}") long initialDelayMillis,
                                           @Value("${mario.nutrition.ai.recommendation.runner.fixed-delay-ms:60000}") long fixedDelayMillis) {
        this.scheduler = scheduler;
        this.aiService = aiService;
        this.initialDelayMillis = Math.max(0L, initialDelayMillis);
        this.fixedDelayMillis = Math.max(1L, fixedDelayMillis);
    }

    public NutritionAiRecommendationRunner(NutritionAiRecommendationScheduler scheduler,
                                           long initialDelayMillis, long fixedDelayMillis) {
        this(scheduler, null, initialDelayMillis, fixedDelayMillis);
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        executorService = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "nutrition-ai-recommendation-runner");
            thread.setDaemon(true);
            return thread;
        });
        executorService.scheduleWithFixedDelay(this::generateSafely, initialDelayMillis, fixedDelayMillis,
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

    private void generateSafely() {
        try {
            scheduler.generateDueRecommendations(LocalDate.now(), LocalTime.now());
        } catch (RuntimeException ex) {
            LogUtil.warn(log).log("nutrition ai recommendation scan failed, error={}", ex.getMessage());
        }
        if (aiService == null) {
            return;
        }
        try {
            aiService.runPendingJobs(10);
        } catch (RuntimeException ex) {
            LogUtil.warn(log).log("nutrition ai recommendation runner failed, error={}", ex.getMessage());
        }
    }
}
