package top.egon.mario.common.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Logback output contains the MDC fields used for traceable logs.
 */
class LogbackConfigurationTests {

    @Test
    void logbackPatternIncludesTraceMdcFields() throws IOException {
        try (var stream = getClass().getClassLoader().getResourceAsStream("logback-spring.xml")) {
            assertThat(stream).isNotNull();
            String logback = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(logback)
                    .contains("%X{appName")
                    .contains("%X{traceId")
                    .contains("%X{requestId");
        }
    }

}
