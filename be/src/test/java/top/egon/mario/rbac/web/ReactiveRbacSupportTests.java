package top.egon.mario.rbac.web;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.TraceContext;

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

    private static class TestRbacSupport extends ReactiveRbacSupport {

        private Mono<ApiResponse<String>> ping() {
            return blocking(() -> "pong");
        }

    }

}
