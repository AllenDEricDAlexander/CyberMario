package top.egon.mario.rbac.web;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.function.Supplier;

/**
 * Wraps blocking JPA service calls for WebFlux controllers.
 */
abstract class ReactiveRbacSupport {

    protected <T> Mono<ApiResponse<T>> blocking(Supplier<T> supplier) {
        return Mono.deferContextual(contextView -> Mono.fromCallable(() -> supplier.get())
                .map(data -> ApiResponse.ok(data, TraceContext.traceId(contextView)))
                .subscribeOn(Schedulers.boundedElastic()));
    }

    protected Mono<ApiResponse<Void>> blockingVoid(Runnable runnable) {
        return Mono.deferContextual(contextView -> Mono.fromRunnable(runnable)
                .thenReturn(ApiResponse.<Void>ok(null, TraceContext.traceId(contextView)))
                .subscribeOn(Schedulers.boundedElastic()));
    }

    protected Long actorId(RbacPrincipal principal) {
        return principal == null ? 0L : principal.userId();
    }

    protected <T> PageResult<T> pageResult(org.springframework.data.domain.Page<T> page) {
        return new PageResult<>(page.getContent(), page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

}
