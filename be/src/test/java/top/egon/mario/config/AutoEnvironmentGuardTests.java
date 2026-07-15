package top.egon.mario.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutoEnvironmentGuardTests {

    @Test
    void registersTheGuardAsAnEnvironmentPostProcessor() throws IOException {
        var properties = PropertiesLoaderUtils.loadProperties(
                new ClassPathResource("META-INF/spring.factories"));

        assertThat(properties.getProperty("org.springframework.boot.env.EnvironmentPostProcessor"))
                .contains("top.egon.mario.config.AutoEnvironmentGuard");
    }

    @Test
    void acceptsDedicatedAutoTargets() {
        assertThatCode(() -> AutoEnvironmentGuard.validate(
                "jdbc:postgresql://localhost:5432/cyber_mario_auto",
                "auto_local",
                15,
                false
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsNonPostgreSqlDatasource() {
        assertThatThrownBy(() -> AutoEnvironmentGuard.validate(
                "jdbc:h2:mem:auto",
                "auto_local",
                15,
                false
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PostgreSQL");
    }

    @Test
    void rejectsDatabaseWithoutAutoBoundary() {
        assertThatThrownBy(() -> AutoEnvironmentGuard.validate(
                "jdbc:postgresql://localhost:5432/cyber_mario",
                "auto_local",
                15,
                false
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Auto database");
    }

    @Test
    void rejectsPublicOrUnscopedSchema() {
        assertThatThrownBy(() -> AutoEnvironmentGuard.validate(
                "jdbc:postgresql://localhost:5432/cyber_mario_auto",
                "public",
                15,
                false
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Auto schema");

        assertThatThrownBy(() -> AutoEnvironmentGuard.validate(
                "jdbc:postgresql://localhost:5432/cyber_mario_auto",
                "quality",
                15,
                false
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Auto schema");
    }

    @Test
    void rejectsDevelopmentRedisDatabase() {
        assertThatThrownBy(() -> AutoEnvironmentGuard.validate(
                "jdbc:postgresql://localhost:5432/cyber_mario_auto",
                "auto_local",
                1,
                false
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Redis database");
    }

    @Test
    void rejectsSecureCookieOnHttpAutoOrigin() {
        assertThatThrownBy(() -> AutoEnvironmentGuard.validate(
                "jdbc:postgresql://localhost:5432/cyber_mario_auto",
                "auto_local",
                15,
                true
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Secure");
    }
}
