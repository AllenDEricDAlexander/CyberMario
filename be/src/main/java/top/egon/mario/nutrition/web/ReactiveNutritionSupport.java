package top.egon.mario.nutrition.web;

import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.function.Supplier;

/**
 * Wraps blocking nutrition service calls for WebFlux controllers.
 */
abstract class ReactiveNutritionSupport {

    private Scheduler blockingScheduler;

    @Autowired
    void setBlockingScheduler(Scheduler blockingScheduler) {
        this.blockingScheduler = blockingScheduler;
    }

    protected <T> Mono<ApiResponse<T>> blocking(Supplier<T> supplier) {
        return Mono.deferContextual(contextView -> {
            String traceId = TraceContext.traceId(contextView);
            return Mono.fromCallable(() -> TraceContext.withMdc(traceId, supplier))
                    .map(data -> ApiResponse.ok(data, traceId))
                    .subscribeOn(blockingScheduler());
        });
    }

    protected Mono<ApiResponse<Void>> blockingVoid(Runnable runnable) {
        return Mono.deferContextual(contextView -> {
            String traceId = TraceContext.traceId(contextView);
            return Mono.fromRunnable(() -> TraceContext.withMdc(traceId, runnable))
                    .thenReturn(ApiResponse.<Void>ok(null, traceId))
                    .subscribeOn(blockingScheduler());
        });
    }

    private Scheduler blockingScheduler() {
        if (blockingScheduler == null) {
            throw new IllegalStateException("blockingScheduler is required");
        }
        return blockingScheduler;
    }

    protected Long actorId(RbacPrincipal principal) {
        return principal == null ? 0L : principal.userId();
    }
}
