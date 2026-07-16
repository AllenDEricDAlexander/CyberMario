package top.egon.mario.investment.marketdata.repository.jdbc;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;

final class JdbcMarketDataSupport {

    static final int MAX_PAGE_SIZE = 10_000;

    private JdbcMarketDataSupport() {
    }

    static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new SQLException("Unsupported timestamp value for " + column + ": " + value);
    }

    /**
     * Converts domain instants to the JDBC 4.2 type supported by the PostgreSQL driver.
     */
    static OffsetDateTime instantParameter(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    static List<OffsetDateTime> instantParameters(Collection<Instant> values) {
        return values.stream().map(JdbcMarketDataSupport::instantParameter).toList();
    }

    static void validatePage(int offset, int limit) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }
        if (limit <= 0 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    static MapSqlParameterSource pageParameters(MapSqlParameterSource parameters, int offset, int limit) {
        validatePage(offset, limit);
        return parameters.addValue("offset", offset).addValue("limit", limit);
    }

    static <T> void requireSingleDimension(List<T> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
    }
}
