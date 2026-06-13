package top.egon.mario.common.utils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies common log context is safe to use around normal SLF4J loggers.
 */
class LogUtilTests {

    private final ch.qos.logback.classic.Logger logger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(LogUtilTests.class);

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        appender = new PreparedListAppender();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        logger.setAdditive(false);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        MDC.clear();
    }

    @Test
    void logBuilderPublishesMdcFieldsForOneLogEventAndRestoresExistingValues() {
        MDC.put("module", "outer");

        LogUtil.info(logger)
                .module("rbac")
                .scene("login")
                .step("verify_password")
                .result("SUCCESS")
                .costMs(15)
                .field("userId", 42)
                .log("login accepted");

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getFormattedMessage()).isEqualTo("login accepted");
        assertThat(event.getMDCPropertyMap())
                .containsEntry("module", "rbac")
                .containsEntry("scene", "login")
                .containsEntry("step", "verify_password")
                .containsEntry("result", "SUCCESS")
                .containsEntry("costMs", "15")
                .containsEntry("userId", "42");
        assertThat(MDC.get("module")).isEqualTo("outer");
        assertThat(MDC.get("scene")).isNull();
    }

    @Test
    void scopeRestoresMdcWhenClosed() {
        MDC.put("traceId", "trace-1");

        try (LogUtil.Scope ignored = LogUtil.context()
                .module("agent")
                .field("token", "secret-token")
                .open()) {
            assertThat(MDC.get("traceId")).isEqualTo("trace-1");
            assertThat(MDC.get("module")).isEqualTo("agent");
            assertThat(MDC.get("token")).isEqualTo("***");
        }

        assertThat(MDC.get("traceId")).isEqualTo("trace-1");
        assertThat(MDC.get("module")).isNull();
        assertThat(MDC.get("token")).isNull();
    }

    @Test
    void formatsValuesForSafeSingleLineLogs() {
        LogUtil.warn(logger)
                .field("bad key", "line1\nline2")
                .field("password", "123456")
                .field("ids", IntStream.range(0, 25).boxed().toList())
                .reject("not allowed");

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getMDCPropertyMap())
                .containsEntry("bad_key", "line1 line2")
                .containsEntry("password", "***")
                .containsEntry("ids", "0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19...total=25")
                .containsEntry("result", "REJECT")
                .containsEntry("reason", "not allowed");
    }

    private static final class PreparedListAppender extends ListAppender<ILoggingEvent> {

        @Override
        protected void append(ILoggingEvent eventObject) {
            eventObject.prepareForDeferredProcessing();
            super.append(eventObject);
        }
    }
}
