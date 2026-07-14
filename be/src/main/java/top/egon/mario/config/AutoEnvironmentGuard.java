package top.egon.mario.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Rejects unsafe Auto targets before DataSource and Flyway initialization.
 */
public class AutoEnvironmentGuard implements EnvironmentPostProcessor, Ordered {

    private static final Pattern AUTO_DATABASE =
            Pattern.compile("(^|[_-])auto([_-]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTO_SCHEMA = Pattern.compile("^auto_[a-z0-9_]+$");

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment,
            SpringApplication application
    ) {
        if (!environment.acceptsProfiles(Profiles.of("auto"))) {
            return;
        }
        validate(
                environment.getProperty("spring.datasource.url"),
                environment.getRequiredProperty("spring.flyway.default-schema"),
                environment.getProperty("spring.data.redis.database", Integer.class, 1),
                environment.getProperty("mario.security.browser-cookie.secure", Boolean.class, true)
        );
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    static void validate(String jdbcUrl, String schema, int redisDatabase, boolean secureCookie) {
        String databaseName = databaseName(jdbcUrl);
        if (!AUTO_DATABASE.matcher(databaseName).find()) {
            throw new IllegalStateException("Auto profile requires a dedicated Auto database");
        }
        if (!StringUtils.hasText(schema)
                || !AUTO_SCHEMA.matcher(schema.toLowerCase(Locale.ROOT)).matches()) {
            throw new IllegalStateException("Auto profile requires an auto_* Auto schema");
        }
        if (redisDatabase <= 1) {
            throw new IllegalStateException("Auto profile requires a Redis database outside the development lane");
        }
        if (secureCookie) {
            throw new IllegalStateException("Auto HTTP origin cannot use Secure browser cookies");
        }
    }

    private static String databaseName(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl) || !jdbcUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("Auto profile requires a PostgreSQL datasource");
        }
        try {
            URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
            String path = uri.getPath();
            if (!StringUtils.hasText(path) || path.length() == 1) {
                throw new IllegalStateException("Auto PostgreSQL URL must include a database name");
            }
            return path.substring(1).split("/", 2)[0];
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Auto PostgreSQL URL is invalid", exception);
        }
    }
}
