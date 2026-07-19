package top.egon.mario.investment.common.job;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Adapts durable-job domain values to JDBC 4.2 types supported by PostgreSQL and H2.
 */
final class InvestmentJobJdbcSupport {

    private InvestmentJobJdbcSupport() {
    }

    static OffsetDateTime instantParameter(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
