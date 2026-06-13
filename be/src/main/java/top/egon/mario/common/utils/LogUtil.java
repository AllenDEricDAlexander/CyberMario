package top.egon.mario.common.utils;

import org.slf4j.Logger;
import org.slf4j.MDC;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Lightweight helpers for structured log context around standard SLF4J loggers.
 */
public final class LogUtil {

    private static final String MASKED_VALUE = "***";
    private static final String EMPTY_VALUE = "-";
    private static final int MAX_VALUE_LENGTH = 1000;
    private static final int MAX_COLLECTION_ITEMS = 20;

    private LogUtil() {
    }

    /**
     * Creates a reusable MDC context builder.
     */
    public static LogContext context() {
        return new LogContext();
    }

    /**
     * Opens an MDC scope with a single field.
     */
    public static Scope with(String key, Object value) {
        return context().field(key, value).open();
    }

    /**
     * Opens an MDC scope with multiple fields.
     */
    public static Scope with(Map<String, ?> fields) {
        return context().fields(fields).open();
    }

    /**
     * Creates a DEBUG log builder.
     */
    public static LogBuilder debug(Logger logger) {
        return new LogBuilder(logger, LogLevel.DEBUG);
    }

    /**
     * Creates an INFO log builder.
     */
    public static LogBuilder info(Logger logger) {
        return new LogBuilder(logger, LogLevel.INFO);
    }

    /**
     * Creates a WARN log builder.
     */
    public static LogBuilder warn(Logger logger) {
        return new LogBuilder(logger, LogLevel.WARN);
    }

    /**
     * Creates an ERROR log builder.
     */
    public static LogBuilder error(Logger logger) {
        return new LogBuilder(logger, LogLevel.ERROR);
    }

    /**
     * Builder for temporary MDC fields.
     */
    public static final class LogContext extends AbstractContext<LogContext> {

        private LogContext() {
        }

        @Override
        protected LogContext self() {
            return this;
        }
    }

    /**
     * Builder that publishes MDC fields for one log event.
     */
    public static final class LogBuilder extends AbstractContext<LogBuilder> {

        private final Logger logger;
        private final LogLevel level;

        private LogBuilder(Logger logger, LogLevel level) {
            this.logger = logger;
            this.level = level;
        }

        @Override
        protected LogBuilder self() {
            return this;
        }

        /**
         * Writes a log event with the builder fields in MDC.
         */
        public void log(String message, Object... arguments) {
            write(level, message, arguments);
        }

        /**
         * Writes a start log event.
         */
        public void start(String message, Object... arguments) {
            result("START");
            write(LogLevel.INFO, message, arguments);
        }

        /**
         * Writes a successful log event.
         */
        public void success(String message, Object... arguments) {
            result("SUCCESS");
            write(level, message, arguments);
        }

        /**
         * Writes an expected rejection as WARN.
         */
        public void reject(String reason, Object... arguments) {
            result("REJECT");
            reason(reason);
            write(LogLevel.WARN, reason, arguments);
        }

        /**
         * Writes an expected warning.
         */
        public void warn(String message, Object... arguments) {
            result("WARN");
            write(LogLevel.WARN, message, arguments);
        }

        /**
         * Writes an unexpected failure as ERROR.
         */
        public void fail(String reason, Throwable throwable) {
            result("FAIL");
            reason(reason);
            if (throwable != null) {
                field("errorMsg", throwable.getMessage());
            }
            write(LogLevel.ERROR, reason, throwable);
        }

        private void write(LogLevel writeLevel, String message, Object... arguments) {
            if (logger == null || writeLevel == null || !writeLevel.isEnabled(logger)) {
                return;
            }

            try (Scope ignored = open()) {
                writeLevel.write(logger, message, arguments);
            }
        }
    }

    /**
     * Temporary MDC scope that restores previous values on close.
     */
    public static final class Scope implements AutoCloseable {

        private final Map<String, String> previousValues;
        private final Set<String> absentKeys;
        private boolean closed;

        private Scope(Map<String, String> previousValues, Set<String> absentKeys) {
            this.previousValues = previousValues;
            this.absentKeys = absentKeys;
        }

        private static Scope open(Map<String, String> fields) {
            Map<String, String> previousValues = new LinkedHashMap<>();
            Set<String> absentKeys = new LinkedHashSet<>();

            fields.forEach((key, value) -> {
                String previousValue = MDC.get(key);
                if (previousValue == null) {
                    absentKeys.add(key);
                } else {
                    previousValues.put(key, previousValue);
                }
                MDC.put(key, value);
            });

            return new Scope(previousValues, absentKeys);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }

            absentKeys.forEach(MDC::remove);
            previousValues.forEach(MDC::put);
            closed = true;
        }
    }

    private abstract static class AbstractContext<T extends AbstractContext<T>> {

        private final LinkedHashMap<String, String> fields = new LinkedHashMap<>();

        protected abstract T self();

        /**
         * Adds the coarse application module.
         */
        public T module(Object module) {
            return field("module", module);
        }

        /**
         * Adds the business scene.
         */
        public T scene(Object scene) {
            return field("scene", scene);
        }

        /**
         * Adds the current action.
         */
        public T action(Object action) {
            return field("action", action);
        }

        /**
         * Adds the current processing step.
         */
        public T step(Object step) {
            return field("step", step);
        }

        /**
         * Adds a business identifier.
         */
        public T bizId(Object bizId) {
            return field("bizId", bizId);
        }

        /**
         * Adds the current result.
         */
        public T result(Object result) {
            return field("result", result);
        }

        /**
         * Adds the current reason.
         */
        public T reason(Object reason) {
            return field("reason", reason);
        }

        /**
         * Adds elapsed time in milliseconds.
         */
        public T costMs(Object costMs) {
            return field("costMs", costMs);
        }

        /**
         * Adds elapsed time from a start timestamp in milliseconds.
         */
        public T costSince(long startTimeMillis) {
            return costMs(System.currentTimeMillis() - startTimeMillis);
        }

        /**
         * Adds one MDC field after key and value normalization.
         */
        public T field(String key, Object value) {
            String normalizedKey = normalizeKey(key);
            String formattedValue = formatValue(normalizedKey, value);
            if (normalizedKey != null && formattedValue != null) {
                fields.put(normalizedKey, formattedValue);
            }
            return self();
        }

        /**
         * Adds multiple MDC fields.
         */
        public T fields(Map<String, ?> values) {
            if (values == null || values.isEmpty()) {
                return self();
            }

            values.forEach(this::field);
            return self();
        }

        /**
         * Opens the current fields as an MDC scope.
         */
        public Scope open() {
            return Scope.open(fields);
        }
    }

    private enum LogLevel {
        DEBUG {
            @Override
            boolean isEnabled(Logger logger) {
                return logger.isDebugEnabled();
            }

            @Override
            void write(Logger logger, String message, Object... arguments) {
                logger.debug(message, arguments);
            }
        }, INFO {
            @Override
            boolean isEnabled(Logger logger) {
                return logger.isInfoEnabled();
            }

            @Override
            void write(Logger logger, String message, Object... arguments) {
                logger.info(message, arguments);
            }
        }, WARN {
            @Override
            boolean isEnabled(Logger logger) {
                return logger.isWarnEnabled();
            }

            @Override
            void write(Logger logger, String message, Object... arguments) {
                logger.warn(message, arguments);
            }
        }, ERROR {
            @Override
            boolean isEnabled(Logger logger) {
                return logger.isErrorEnabled();
            }

            @Override
            void write(Logger logger, String message, Object... arguments) {
                logger.error(message, arguments);
            }
        };

        abstract boolean isEnabled(Logger logger);

        abstract void write(Logger logger, String message, Object... arguments);
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return key.trim().replaceAll("[^a-zA-Z0-9_.-]", "_");
    }

    private static String formatValue(String key, Object value) {
        if (value == null) {
            return null;
        }

        String text = objectToString(value);
        if (text == null || text.isBlank()) {
            return null;
        }

        text = normalizeValue(text);
        text = maskIfNecessary(key, text);
        return truncate(text, MAX_VALUE_LENGTH);
    }

    private static String objectToString(Object value) {
        if (value instanceof Collection<?> collection) {
            return collectionToString(collection);
        }

        if (value instanceof Map<?, ?> map) {
            return mapToString(map);
        }

        if (value.getClass().isArray()) {
            return arrayToString(value);
        }

        return String.valueOf(value);
    }

    private static String collectionToString(Collection<?> collection) {
        if (collection.isEmpty()) {
            return EMPTY_VALUE;
        }

        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (Object item : collection) {
            if (count >= MAX_COLLECTION_ITEMS) {
                break;
            }
            appendDelimitedValue(builder, item, count);
            count++;
        }
        appendTotal(builder, collection.size());
        return builder.toString();
    }

    private static String arrayToString(Object array) {
        int length = Array.getLength(array);
        if (length == 0) {
            return EMPTY_VALUE;
        }

        StringBuilder builder = new StringBuilder();
        int max = Math.min(length, MAX_COLLECTION_ITEMS);
        for (int i = 0; i < max; i++) {
            appendDelimitedValue(builder, Array.get(array, i), i);
        }
        appendTotal(builder, length);
        return builder.toString();
    }

    private static String mapToString(Map<?, ?> map) {
        if (map.isEmpty()) {
            return EMPTY_VALUE;
        }

        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (count >= MAX_COLLECTION_ITEMS) {
                break;
            }
            if (count > 0) {
                builder.append(',');
            }
            builder.append(entry.getKey()).append(':').append(entry.getValue());
            count++;
        }
        appendTotal(builder, map.size());
        return builder.toString();
    }

    private static void appendDelimitedValue(StringBuilder builder, Object value, int index) {
        if (index > 0) {
            builder.append(',');
        }
        builder.append(value);
    }

    private static void appendTotal(StringBuilder builder, int size) {
        if (size > MAX_COLLECTION_ITEMS) {
            builder.append("...total=").append(size);
        }
    }

    private static String normalizeValue(String value) {
        return value.trim().replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
    }

    private static String maskIfNecessary(String key, String value) {
        if (key == null || value == null) {
            return value;
        }

        String lowerKey = key.toLowerCase();
        if (lowerKey.contains("password") || lowerKey.contains("passwd") || lowerKey.contains("pwd") || lowerKey.contains("token") || lowerKey.contains("secret") || lowerKey.contains("authorization") || lowerKey.contains("cookie")) {
            return MASKED_VALUE;
        }

        if (lowerKey.contains("phone") || lowerKey.contains("mobile")) {
            return maskMiddle(value, 3, 4);
        }

        if (lowerKey.contains("email")) {
            return maskEmail(value);
        }

        return value;
    }

    private static String maskMiddle(String value, int prefixLength, int suffixLength) {
        if (value.length() <= prefixLength + suffixLength) {
            return MASKED_VALUE;
        }
        return value.substring(0, prefixLength) + "****" + value.substring(value.length() - suffixLength);
    }

    private static String maskEmail(String value) {
        int index = value.indexOf('@');
        if (index <= 0) {
            return MASKED_VALUE;
        }
        return value.charAt(0) + "****" + value.substring(index);
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...truncated";
    }
}
