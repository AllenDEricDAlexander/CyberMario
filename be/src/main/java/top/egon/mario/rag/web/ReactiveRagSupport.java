package top.egon.mario.rag.web;

import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.function.Supplier;

/**
 * Wraps blocking RAG service calls for WebFlux controllers.
 */
abstract class ReactiveRagSupport {

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

    protected <T> PageResult<T> pageResult(org.springframework.data.domain.Page<T> page) {
        return new PageResult<>(page.getContent(), page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    protected Long actorId(RbacPrincipal principal) {
        return principal == null ? 0L : principal.userId();
    }

}
