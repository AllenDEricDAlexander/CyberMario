package top.egon.mario.agent.externalim.runtime;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class MemorySpaceExecutionLane {

    private final ConcurrentHashMap<String, CompletableFuture<Void>> tails = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("external-im-space-", 0).factory());

    public CompletableFuture<Void> submit(String memorySpaceId, Runnable task) {
        if (!StringUtils.hasText(memorySpaceId) || task == null) {
            throw new IllegalArgumentException("memorySpaceId and task are required");
        }
        CompletableFuture<Void> next = tails.compute(memorySpaceId, (key, tail) -> {
            CompletableFuture<Void> base = tail == null
                    ? CompletableFuture.completedFuture(null)
                    : tail.handle((ignored, error) -> null);
            return base.thenRunAsync(task, executor);
        });
        next.whenComplete((ignored, error) -> tails.remove(memorySpaceId, next));
        return next;
    }

    @PreDestroy
    void close() {
        executor.close();
    }
}
