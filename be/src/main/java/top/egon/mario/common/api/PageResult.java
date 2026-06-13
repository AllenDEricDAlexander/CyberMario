package top.egon.mario.common.api;

import java.util.List;

/**
 * Stable page payload returned from paged list APIs.
 */
public record PageResult<T>(
        List<T> records,
        int page,
        int size,
        long total,
        int totalPages
) {
}
