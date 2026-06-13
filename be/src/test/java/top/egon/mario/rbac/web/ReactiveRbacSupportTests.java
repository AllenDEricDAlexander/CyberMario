package top.egon.mario.rbac.web;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.TraceContext;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies RBAC WebFlux response wrapping keeps Reactor context across blocking work.
 */
class ReactiveRbacSupportTests {

    private final TestRbacSupport support = new TestRbacSupport();

    @Test
    void blockingResponseUsesTraceIdFromReactorContext() {
        StepVerifier.create(support.ping()
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-from-filter")))
                .assertNext(response -> {
                    assertThat(response.data()).isEqualTo("pong");
                    assertThat(response.traceId()).isEqualTo("trace-from-filter");
                })
                .verifyComplete();
    }

    @Test
    void blockingWorkPublishesTraceIdToMdcOnWorkerThreadAndClearsItAfterCompletion() {
        AtomicReference<String> workerTraceId = new AtomicReference<>();

        StepVerifier.create(support.pingWithMdc(workerTraceId)
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-from-filter")))
                .assertNext(response -> {
                    assertThat(response.data()).isEqualTo("pong");
                    assertThat(workerTraceId).hasValue("trace-from-filter");
                })
                .verifyComplete();
        assertThat(MDC.get("traceId")).isNull();
    }

    private static class TestRbacSupport extends ReactiveRbacSupport {

        private Mono<ApiResponse<String>> ping() {
            return blocking(() -> "pong");
        }

        private Mono<ApiResponse<String>> pingWithMdc(AtomicReference<String> workerTraceId) {
            return blocking(() -> {
                workerTraceId.set(MDC.get("traceId"));
                return "pong";
            });
        }

    }

}
