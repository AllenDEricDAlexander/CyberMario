package top.egon.mario.clocktower.common.web;

import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.function.Supplier;

public abstract class ClocktowerReactiveSupport {

    private Scheduler blockingScheduler;

    @Autowired
    void setBlockingScheduler(Scheduler blockingScheduler) {
        this.blockingScheduler = blockingScheduler;
    }

    protected <T> Mono<T> blocking(Supplier<T> supplier) {
        return Mono.fromCallable(supplier::get).subscribeOn(blockingScheduler());
    }

    protected Mono<Void> blockingVoid(Runnable runnable) {
        return Mono.fromRunnable(runnable).subscribeOn(blockingScheduler()).then();
    }

    private Scheduler blockingScheduler() {
        if (blockingScheduler == null) {
            throw new IllegalStateException("blockingScheduler is required");
        }
        return blockingScheduler;
    }
}
