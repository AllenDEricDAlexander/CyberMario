package top.egon.mario.investment.common.job;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * Keeps JSON value validation and JDBC JSON binding consistent across PostgreSQL and H2 tests.
 */
@Component
class InvestmentJobJsonSupport {

    private final ObjectMapper objectMapper;
    private final DatabaseKind databaseKind;

    InvestmentJobJsonSupport(ObjectMapper objectMapper, DataSourceProperties dataSourceProperties) {
        this.objectMapper = objectMapper;
        String url = dataSourceProperties == null ? null : dataSourceProperties.getUrl();
        Assert.state(StringUtils.hasText(url), "Investment job database URL must be configured");
        if (url.startsWith("jdbc:postgresql:")) {
            this.databaseKind = DatabaseKind.POSTGRESQL;
        } else if (url.startsWith("jdbc:h2:")) {
            this.databaseKind = DatabaseKind.H2;
        } else {
            throw new IllegalStateException("Unsupported Investment job database: " + url);
        }
    }

    String normalize(String json, String fieldName) {
        Assert.hasText(json, fieldName + " must not be blank");
        try (JsonParser parser = objectMapper.getFactory().createParser(json)) {
            JsonNode value = objectMapper.readTree(parser);
            if (value == null || parser.nextToken() != null) {
                throw new IllegalArgumentException(fieldName + " must contain exactly one JSON value");
            }
            return objectMapper.writeValueAsString(value);
        } catch (IOException ex) {
            throw new IllegalArgumentException(fieldName + " must contain exactly one valid JSON value", ex);
        }
    }

    String writeExpression(String parameterName) {
        return databaseKind == DatabaseKind.POSTGRESQL
                ? "cast(:" + parameterName + " as jsonb)"
                : ":" + parameterName + " format json";
    }

    boolean supportsOnConflict() {
        return databaseKind == DatabaseKind.POSTGRESQL;
    }

    boolean supportsSkipLocked() {
        return databaseKind == DatabaseKind.POSTGRESQL;
    }

    boolean supportsDatabaseClock() {
        return databaseKind == DatabaseKind.POSTGRESQL;
    }

    private enum DatabaseKind {
        POSTGRESQL,
        H2
    }
}
