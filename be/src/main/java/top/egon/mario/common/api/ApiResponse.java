package top.egon.mario.common.api;

import java.time.OffsetDateTime;
import java.util.UUID;

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
        return new ApiResponse<>("0", "OK", data, UUID.randomUUID().toString(), OffsetDateTime.now());
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(code, message, null, UUID.randomUUID().toString(), OffsetDateTime.now());
    }

}
