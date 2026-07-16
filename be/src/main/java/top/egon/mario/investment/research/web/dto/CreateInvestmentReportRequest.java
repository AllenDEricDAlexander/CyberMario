package top.egon.mario.investment.research.web.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/**
 * Bounded request for one code-owned report generator.
 */
public record CreateInvestmentReportRequest(
        @NotBlank String reportType,
        Long instrumentId,
        String priceType,
        String interval,
        Instant fromInclusive,
        Instant toExclusive
) {
}
