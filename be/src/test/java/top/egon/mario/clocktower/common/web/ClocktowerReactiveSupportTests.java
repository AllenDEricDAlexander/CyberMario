package top.egon.mario.clocktower.common.web;

import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.TraceContext;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ClocktowerReactiveSupportTests {

    @Test
    void blockingWrapsJsonResponsesInStandardApiEnvelope() {
        TestSupport support = new TestSupport();
        support.setBlockingScheduler(Schedulers.immediate());

        StepVerifier.create(support.ping()
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-clocktower")))
                .assertNext(item -> {
                    assertThat(item).isInstanceOf(ApiResponse.class);
                    @SuppressWarnings("unchecked")
                    ApiResponse<String> response = (ApiResponse<String>) item;
                    assertThat(response.code()).isEqualTo("0");
                    assertThat(response.data()).isEqualTo("pong");
                    assertThat(response.traceId()).isEqualTo("trace-clocktower");
                })
                .verifyComplete();
    }

    @Test
    void blockingRunsSupplierWithTraceMdc() {
        TestSupport support = new TestSupport();
        support.setBlockingScheduler(Schedulers.immediate());
        AtomicReference<String> workerTraceId = new AtomicReference<>();

        StepVerifier.create(support.trace(workerTraceId)
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-clocktower")))
                .assertNext(item -> assertThat(((ApiResponse<?>) item).traceId()).isEqualTo("trace-clocktower"))
                .verifyComplete();

        assertThat(workerTraceId).hasValue("trace-clocktower");
    }

    private static final class TestSupport extends ClocktowerReactiveSupport {

        reactor.core.publisher.Mono<?> ping() {
            return blocking(() -> "pong");
        }

        reactor.core.publisher.Mono<?> trace(AtomicReference<String> workerTraceId) {
            return blocking(() -> {
                workerTraceId.set(org.slf4j.MDC.get(TraceContext.TRACE_ID_MDC_KEY));
                return "pong";
            });
        }
    }
}
