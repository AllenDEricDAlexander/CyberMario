package top.egon.mario.common.api;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies request trace propagation for standard API responses.
 */
class TraceWebFilterTests {

    private final TraceWebFilter traceWebFilter = new TraceWebFilter();

    @Test
    void keepsFrontendTraceIdAndPublishesItToRequestResponseAndReactorContext() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/demo")
                .header(TraceContext.TRACE_ID_HEADER, "front-trace-1")
                .build());
        AtomicReference<String> requestTraceId = new AtomicReference<>();
        AtomicReference<String> contextTraceId = new AtomicReference<>();

        traceWebFilter.filter(exchange, chainExchange -> captureTrace(chainExchange, requestTraceId, contextTraceId)).block();

        assertThat(requestTraceId).hasValue("front-trace-1");
        assertThat(contextTraceId).hasValue("front-trace-1");
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceContext.TRACE_ID_HEADER)).isEqualTo("front-trace-1");
    }

    @Test
    void publishesTraceIdToMdcDuringRequestAndClearsItAfterCompletion() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/demo")
                .header(TraceContext.TRACE_ID_HEADER, "front-trace-1")
                .build());
        AtomicReference<String> mdcTraceId = new AtomicReference<>();

        traceWebFilter.filter(exchange, chainExchange -> Mono.fromRunnable(() -> mdcTraceId.set(MDC.get("traceId")))).block();

        assertThat(mdcTraceId).hasValue("front-trace-1");
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void createsTraceIdWhenRequestDoesNotProvideOne() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/demo").build());
        AtomicReference<String> requestTraceId = new AtomicReference<>();
        AtomicReference<String> contextTraceId = new AtomicReference<>();

        traceWebFilter.filter(exchange, chainExchange -> captureTrace(chainExchange, requestTraceId, contextTraceId)).block();

        assertThat(requestTraceId.get()).isNotBlank();
        assertThat(contextTraceId).hasValue(requestTraceId.get());
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceContext.TRACE_ID_HEADER)).isEqualTo(requestTraceId.get());
    }

    @Test
    void apiResponseUsesTraceIdFromReactorContext() {
        Mono<ApiResponse<String>> response = Mono.deferContextual(contextView ->
                Mono.just(ApiResponse.ok("pong", TraceContext.traceId(contextView))));

        ApiResponse<String> apiResponse = response
                .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "context-trace-1"))
                .block();

        assertThat(apiResponse).isNotNull();
        assertThat(apiResponse.traceId()).isEqualTo("context-trace-1");
    }

    private Mono<Void> captureTrace(ServerWebExchange exchange, AtomicReference<String> requestTraceId,
                                    AtomicReference<String> contextTraceId) {
        return Mono.deferContextual(contextView -> {
            requestTraceId.set(exchange.getRequest().getHeaders().getFirst(TraceContext.TRACE_ID_HEADER));
            contextTraceId.set(TraceContext.traceId(contextView));
            return Mono.empty();
        });
    }

}
