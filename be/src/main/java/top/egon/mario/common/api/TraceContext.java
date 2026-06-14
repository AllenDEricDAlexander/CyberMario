package top.egon.mario.common.api;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import reactor.util.context.ContextView;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Trace identifiers shared by WebFlux filters and standard API responses.
 */
public final class TraceContext {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String CONTEXT_KEY = TraceContext.class.getName() + ".TRACE_ID";
    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    private TraceContext() {
    }

    public static String resolve(HttpHeaders headers) {
        String traceId = firstText(headers.getFirst(TRACE_ID_HEADER));
        if (traceId != null) {
            return traceId;
        }
        String requestId = firstText(headers.getFirst(REQUEST_ID_HEADER));
        return requestId == null ? newTraceId() : requestId;
    }

    public static String traceId(ContextView contextView) {
        return contextView.hasKey(CONTEXT_KEY) ? contextView.get(CONTEXT_KEY) : newTraceId();
    }

    public static String newTraceId() {
        return UUID.randomUUID().toString();
    }

    public static void putMdc(String traceId) {
        String cleanTraceId = firstText(traceId);
        if (cleanTraceId == null) {
            clearMdc();
            return;
        }
        MDC.put(TRACE_ID_MDC_KEY, cleanTraceId);
        MDC.put(REQUEST_ID_MDC_KEY, cleanTraceId);
    }

    public static void clearMdc() {
        MDC.remove(TRACE_ID_MDC_KEY);
        MDC.remove(REQUEST_ID_MDC_KEY);
    }

    public static <T> T withMdc(String traceId, Supplier<T> supplier) {
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        putMdc(traceId);
        try {
            return supplier.get();
        } finally {
            restoreMdc(previousContext);
        }
    }

    public static void withMdc(String traceId, Runnable runnable) {
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        putMdc(traceId);
        try {
            runnable.run();
        } finally {
            restoreMdc(previousContext);
        }
    }

    private static String firstText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static void restoreMdc(Map<String, String> previousContext) {
        if (previousContext == null) {
            MDC.clear();
            return;
        }
        MDC.setContextMap(previousContext);
    }

}
