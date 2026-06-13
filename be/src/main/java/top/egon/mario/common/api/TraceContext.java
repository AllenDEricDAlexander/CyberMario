package top.egon.mario.common.api;

import org.springframework.http.HttpHeaders;
import reactor.util.context.ContextView;

import java.util.UUID;

/**
 * Trace identifiers shared by WebFlux filters and standard API responses.
 */
public final class TraceContext {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String CONTEXT_KEY = TraceContext.class.getName() + ".TRACE_ID";

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

    private static String firstText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

}
