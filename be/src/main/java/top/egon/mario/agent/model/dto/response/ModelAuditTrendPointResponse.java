package top.egon.mario.agent.model.dto.response;

import java.time.LocalDate;

/**
 * Chart-ready daily metric point.
 */
public record ModelAuditTrendPointResponse(
        LocalDate date,
        String metric,
        long value
) {
}
