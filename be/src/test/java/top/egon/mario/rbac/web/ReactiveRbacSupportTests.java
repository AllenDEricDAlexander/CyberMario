package top.egon.mario.rbac.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.TraceContext;

import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies RBAC WebFlux response wrapping keeps Reactor context across blocking work.
 */
class ReactiveRbacSupportTests {

    private final TestRbacSupport support = new TestRbacSupport();

    @AfterEach
    void tearDown() {
        support.dispose();
    }

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

    @Test
    void blockingWorkUsesConfiguredVirtualThreadScheduler() {
        AtomicReference<String> workerThreadName = new AtomicReference<>();
        AtomicReference<Boolean> workerThreadVirtual = new AtomicReference<>();

        StepVerifier.create(support.threadName(workerThreadName, workerThreadVirtual)
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-from-filter")))
                .assertNext(response -> {
                    assertThat(response.data()).isNotBlank();
                    assertThat(workerThreadVirtual).hasValue(true);
                    assertThat(workerThreadName.get()).contains("test-blocking-virtual");
                })
                .verifyComplete();
    }

    private static class TestRbacSupport extends ReactiveRbacSupport {

        private final Scheduler testScheduler = Schedulers.fromExecutorService(
                Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("test-blocking-virtual-", 0).factory()),
                "test-blocking-virtual"
        );

        private TestRbacSupport() {
            setBlockingScheduler(testScheduler);
        }

        private void dispose() {
            testScheduler.dispose();
        }

        private Mono<ApiResponse<String>> ping() {
            return blocking(() -> "pong");
        }

        private Mono<ApiResponse<String>> pingWithMdc(AtomicReference<String> workerTraceId) {
            return blocking(() -> {
                workerTraceId.set(MDC.get("traceId"));
                return "pong";
            });
        }

        private Mono<ApiResponse<String>> threadName(AtomicReference<String> workerThreadName,
                                                     AtomicReference<Boolean> workerThreadVirtual) {
            return blocking(() -> {
                workerThreadName.set(Thread.currentThread().getName());
                workerThreadVirtual.set(Thread.currentThread().isVirtual());
                return workerThreadName.get();
            });
        }

    }

}
