package top.egon.mario.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Provides the scheduler used when reactive pipelines delegate to blocking APIs.
 */
@Configuration
public class BlockingSchedulerConfig {

    /**
     * Creates virtual threads for blocking calls made from reactive pipelines.
     */
    @Bean(destroyMethod = "dispose")
    public Scheduler blockingScheduler() {
        ExecutorService executorService = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("blocking-virtual-", 0).factory()
        );
        return Schedulers.fromExecutorService(executorService, "blocking-virtual");
    }

}
