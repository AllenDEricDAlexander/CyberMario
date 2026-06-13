package top.egon.mario.common.api;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Standard response body used by application APIs.
 */
public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String traceId,
        OffsetDateTime timestamp
) {

    public static <T> ApiResponse<T> ok(T data) {
        return ok(data, TraceContext.newTraceId());
    }

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>("0", "OK", data, traceId, OffsetDateTime.now(ZoneOffset.UTC));
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return fail(code, message, TraceContext.newTraceId());
    }

    public static <T> ApiResponse<T> fail(String code, String message, String traceId) {
        return new ApiResponse<>(code, message, null, traceId, OffsetDateTime.now(ZoneOffset.UTC));
    }

}
