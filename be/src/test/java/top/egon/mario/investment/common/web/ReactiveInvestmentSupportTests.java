package top.egon.mario.investment.common.web;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.TraceContext;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ReactiveInvestmentSupportTests {

    @Test
    void blockingDispatchesWorkAndPreservesTraceContext() {
        Scheduler scheduler = Schedulers.newSingle("investment-blocking-test");
        try {
            TestSupport support = new TestSupport();
            support.setBlockingScheduler(scheduler);
            AtomicReference<String> workerThread = new AtomicReference<>();
            AtomicReference<String> workerTraceId = new AtomicReference<>();

            StepVerifier.create(support.blockingValue(workerThread, workerTraceId)
                            .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-investment")))
                    .assertNext(response -> {
                        assertThat(response.code()).isEqualTo("0");
                        assertThat(response.data()).isEqualTo("ready");
                        assertThat(response.traceId()).isEqualTo("trace-investment");
                    })
                    .verifyComplete();

            assertThat(workerThread).hasValueSatisfying(thread -> assertThat(thread).contains("investment-blocking-test"));
            assertThat(workerTraceId).hasValue("trace-investment");
        } finally {
            scheduler.dispose();
        }
    }

    @Test
    void blockingVoidUsesTheSameApiEnvelope() {
        TestSupport support = new TestSupport();
        support.setBlockingScheduler(Schedulers.immediate());
        AtomicReference<String> effect = new AtomicReference<>();

        StepVerifier.create(support.blockingEffect(effect)
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-void")))
                .assertNext(response -> {
                    assertThat(response.code()).isEqualTo("0");
                    assertThat(response.data()).isNull();
                    assertThat(response.traceId()).isEqualTo("trace-void");
                })
                .verifyComplete();

        assertThat(effect).hasValue("done");
    }

    @Test
    void missingBlockingSchedulerFailsClosed() {
        TestSupport support = new TestSupport();

        StepVerifier.create(support.blockingValue(new AtomicReference<>(), new AtomicReference<>()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("blockingScheduler is required"))
                .verify();
    }

    private static final class TestSupport extends ReactiveInvestmentSupport {

        reactor.core.publisher.Mono<ApiResponse<String>> blockingValue(
                AtomicReference<String> workerThread,
                AtomicReference<String> workerTraceId
        ) {
            return blocking(() -> {
                workerThread.set(Thread.currentThread().getName());
                workerTraceId.set(MDC.get(TraceContext.TRACE_ID_MDC_KEY));
                return "ready";
            });
        }

        reactor.core.publisher.Mono<ApiResponse<Void>> blockingEffect(AtomicReference<String> effect) {
            return blockingVoid(() -> effect.set("done"));
        }
    }
}
