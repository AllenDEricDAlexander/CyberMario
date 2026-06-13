package top.egon.mario.rbac.web;

import lombok.extern.slf4j.Slf4j;
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
@Slf4j
abstract class ReactiveRbacSupport {

    protected <T> Mono<ApiResponse<T>> blocking(Supplier<T> supplier) {
        return Mono.deferContextual(contextView -> {
            String traceId = TraceContext.traceId(contextView);
            return Mono.fromCallable(() -> TraceContext.withMdc(traceId, supplier))
                    .map(data -> ApiResponse.ok(data, traceId))
                    .subscribeOn(Schedulers.boundedElastic());
        });
    }

    protected Mono<ApiResponse<Void>> blockingVoid(Runnable runnable) {
        return Mono.deferContextual(contextView -> {
            String traceId = TraceContext.traceId(contextView);
            return Mono.fromRunnable(() -> TraceContext.withMdc(traceId, runnable))
                    .thenReturn(ApiResponse.<Void>ok(null, traceId))
                    .subscribeOn(Schedulers.boundedElastic());
        });
    }

    protected Long actorId(RbacPrincipal principal) {
        return principal == null ? 0L : principal.userId();
    }

    protected <T> PageResult<T> pageResult(org.springframework.data.domain.Page<T> page) {
        return new PageResult<>(page.getContent(), page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

}
