package com.moud.server.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MoudLogger {
    public static final String RESET = "\u001B[0m";
    public static final String BLACK = "\u001B[30m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String PURPLE = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";
    public static final String BOLD = "\u001B[1m";
    public static final String DIM = "\u001B[2m";
    public static final String BRIGHT_RED = "\u001B[91m";
    public static final String BRIGHT_GREEN = "\u001B[92m";
    public static final String BRIGHT_YELLOW = "\u001B[93m";
    public static final String BRIGHT_BLUE = "\u001B[94m";
    public static final String BRIGHT_MAGENTA = "\u001B[95m";
    public static final String BRIGHT_CYAN = "\u001B[96m";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Marker SUCCESS_MARKER = MarkerFactory.getMarker("SUCCESS");
    private static final Marker SCRIPT_ERROR_MARKER = MarkerFactory.getMarker("SCRIPT_ERROR");
    private static final Marker CRITICAL_MARKER = MarkerFactory.getMarker("CRITICAL");
    private static final Marker API_MARKER = MarkerFactory.getMarker("API");
    private static final Marker NETWORK_MARKER = MarkerFactory.getMarker("NETWORK");
    private static final Marker STRUCTURED_MARKER = MarkerFactory.getMarker("STRUCTURED");

    private static final ConcurrentHashMap<String, RateLimitState> RATE_LIMITS = new ConcurrentHashMap<>();

    private final Logger logger;
    private final String className;
    private final LogContext baseContext;

    private MoudLogger(Logger logger, String className, LogContext baseContext) {
        this.logger = logger;
        this.className = className;
        this.baseContext = baseContext;
    }

    public static MoudLogger getLogger(Class<?> clazz) {
        return new MoudLogger(LoggerFactory.getLogger(clazz), clazz.getSimpleName(), LogContext.empty());
    }

    public static MoudLogger getLogger(String name) {
        return new MoudLogger(LoggerFactory.getLogger(name), name, LogContext.empty());
    }

    public static MoudLogger getLogger(Class<?> clazz, LogContext baseContext) {
        return new MoudLogger(LoggerFactory.getLogger(clazz), clazz.getSimpleName(), baseContext == null ? LogContext.empty() : baseContext);
    }

    public MoudLogger withContext(LogContext context) {
        return new MoudLogger(this.logger, this.className, this.baseContext.merge(context == null ? LogContext.empty() : context));
    }

    public void trace(String message, Object... args) {
        trace(LogContext.empty(), message, args);
    }

    public void trace(LogContext context, String message, Object... args) {
        log(LogLevel.TRACE, null, "TRACE", DIM + CYAN, message, context, null, args);
    }

    public void info(String message, Object... args) {
        info(LogContext.empty(), message, args);
    }

    public void info(LogContext context, String message, Object... args) {
        log(LogLevel.INFO, null, "INFO", BLUE, message, context, null, args);
    }

    public void debug(String message, Object... args) {
        debug(LogContext.empty(), message, args);
    }

    public void debug(LogContext context, String message, Object... args) {
        log(LogLevel.DEBUG, null, "DEBUG", DIM + WHITE, message, context, null, args);
    }

    public void warn(String message, Object... args) {
        warn(LogContext.empty(), message, args);
    }

    public void warn(LogContext context, String message, Object... args) {
        log(LogLevel.WARN, null, "WARN", YELLOW, message, context, null, args);
    }

    public void error(String message, Object... args) {
        error(LogContext.empty(), message, null, args);
    }

    public void error(LogContext context, String message, Throwable throwable, Object... args) {
        log(LogLevel.ERROR, null, "ERROR", RED, message, context, throwable, args);
    }

    public void error(String message, Throwable throwable, Object... args) {
        error(LogContext.empty(), message, throwable, args);
    }

    public void success(String message, Object... args) {
        LogContext ctx = LogContext.builder()
                .put("status", "success")
                .build();
        log(LogLevel.INFO, SUCCESS_MARKER, "SUCCESS", BRIGHT_GREEN, message, ctx, null, args);
    }

    public void critical(String message, Object... args) {
        LogContext ctx = LogContext.builder()
                .put("severity", "critical")
                .build();
        log(LogLevel.ERROR, CRITICAL_MARKER, "CRITICAL", BOLD + BRIGHT_RED, message, ctx, null, args);
    }

    public void scriptError(String message, Object... args) {
        LogContext ctx = LogContext.builder()
                .put("subsystem", "script")
                .build();
        log(LogLevel.ERROR, SCRIPT_ERROR_MARKER, "SCRIPT", BRIGHT_MAGENTA, message, ctx, null, args);
    }

    public void api(String message, Object... args) {
        LogContext ctx = LogContext.builder()
                .put("subsystem", "api")
                .build();
        log(LogLevel.INFO, API_MARKER, "API", CYAN, message, ctx, null, args);
    }

    public void network(String message, Object... args) {
        RateLimitResult rate = rateLimit("network:" + className, 250);
        if (!rate.shouldLog()) {
            return;
        }
        LogContext.Builder builder = LogContext.builder()
                .put("subsystem", "network");
        if (rate.suppressedCount() > 0) {
            builder.put("suppressed_logs", rate.suppressedCount());
        }
        log(LogLevel.INFO, NETWORK_MARKER, "NET", BRIGHT_CYAN, message, builder.build(), null, args);
    }

    public void startup(String message, Object... args) {
        LogContext ctx = LogContext.builder()
                .put("lifecycle", "startup")
                .build();
        log(LogLevel.INFO, null, "STARTUP", BOLD + BRIGHT_GREEN, message, ctx, null, args);
    }

    public void shutdown(String message, Object... args) {
        LogContext ctx = LogContext.builder()
                .put("lifecycle", "shutdown")
                .build();
        log(LogLevel.INFO, null, "SHUTDOWN", BOLD + YELLOW, message, ctx, null, args);
    }

    private void log(LogLevel level, Marker marker, String label, String color, String message,
                     LogContext context, Throwable throwable, Object... args) {
        if (!isEnabled(level)) {
            return;
        }

        LogContext mergedContext = baseContext.merge(context == null ? LogContext.empty() : context);
        FormattingTuple tuple = MessageFormatter.arrayFormat(message, args);
        if (throwable == null && tuple.getThrowable() != null) {
            throwable = tuple.getThrowable();
        }

        String renderedMessage = tuple.getMessage() != null ? tuple.getMessage() : message;
        String sanitizedMessage = LogRedactor.redact(renderedMessage);
        Map<String, Object> sanitizedContext = LogRedactor.redactContext(mergedContext.asMap());

        String consoleMessage = formatMessage(label, color, sanitizedMessage);
        if (!sanitizedContext.isEmpty()) {
            consoleMessage = consoleMessage + " " + sanitizedContext;
        }

        logToSlf4j(level, marker, consoleMessage, throwable);
        emitStructured(level, sanitizedMessage, sanitizedContext, throwable);
    }

    private void logToSlf4j(LogLevel level, Marker marker, String message, Throwable throwable) {
        switch (level) {
            case TRACE -> {
                if (throwable != null) {
                    if (marker != null) {
                        logger.trace(marker, message, throwable);
                    } else {
                        logger.trace(message, throwable);
                    }
                } else {
                    if (marker != null) {
                        logger.trace(marker, message);
                    } else {
                        logger.trace(message);
                    }
                }
            }
            case DEBUG -> {
                if (throwable != null) {
                    if (marker != null) {
                        logger.debug(marker, message, throwable);
                    } else {
                        logger.debug(message, throwable);
                    }
                } else {
                    if (marker != null) {
                        logger.debug(marker, message);
                    } else {
                        logger.debug(message);
                    }
                }
            }
            case INFO -> {
                if (throwable != null) {
                    if (marker != null) {
                        logger.info(marker, message, throwable);
                    } else {
                        logger.info(message, throwable);
                    }
                } else {
                    if (marker != null) {
                        logger.info(marker, message);
                    } else {
                        logger.info(message);
                    }
                }
            }
            case WARN -> {
                if (throwable != null) {
                    if (marker != null) {
                        logger.warn(marker, message, throwable);
                    } else {
                        logger.warn(message, throwable);
                    }
                } else {
                    if (marker != null) {
                        logger.warn(marker, message);
                    } else {
                        logger.warn(message);
                    }
                }
            }
            case ERROR -> {
                if (throwable != null) {
                    if (marker != null) {
                        logger.error(marker, message, throwable);
                    } else {
                        logger.error(message, throwable);
                    }
                } else {
                    if (marker != null) {
                        logger.error(marker, message);
                    } else {
                        logger.error(message);
                    }
                }
            }
        }
    }

    private void emitStructured(LogLevel level, String message, Map<String, Object> context, Throwable throwable) {
        try {
            ObjectNode node = OBJECT_MAPPER.createObjectNode();
            node.put("timestamp", Instant.now().toString());
            node.put("level", level.name());
            node.put("logger", logger.getName());
            node.put("category", className);
            node.put("message", message);
            node.put("thread", Thread.currentThread().getName());
            if (!context.isEmpty()) {
                node.set("context", OBJECT_MAPPER.valueToTree(context));
            }
            if (throwable != null) {
                ObjectNode errorNode = node.putObject("error");
                errorNode.put("type", throwable.getClass().getName());
                errorNode.put("message", throwable.getMessage());
            }

            String json = OBJECT_MAPPER.writeValueAsString(node);
            switch (level) {
                case TRACE -> logger.trace(STRUCTURED_MARKER, json);
                case DEBUG -> logger.debug(STRUCTURED_MARKER, json);
                case INFO -> logger.info(STRUCTURED_MARKER, json);
                case WARN -> {
                    if (throwable != null) {
                        logger.warn(STRUCTURED_MARKER, json, throwable);
                    } else {
                        logger.warn(STRUCTURED_MARKER, json);
                    }
                }
                case ERROR -> {
                    if (throwable != null) {
                        logger.error(STRUCTURED_MARKER, json, throwable);
                    } else {
                        logger.error(STRUCTURED_MARKER, json);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to emit structured log payload", e);
        }
    }

    private boolean isEnabled(LogLevel level) {
        return switch (level) {
            case TRACE -> logger.isTraceEnabled();
            case DEBUG -> logger.isDebugEnabled();
            case INFO -> logger.isInfoEnabled();
            case WARN -> logger.isWarnEnabled();
            case ERROR -> logger.isErrorEnabled();
        };
    }

    private RateLimitResult rateLimit(String key, long throttleMs) {
        RateLimitState state = RATE_LIMITS.computeIfAbsent(key, k -> new RateLimitState());
        long now = System.currentTimeMillis();
        return state.tryAcquire(now, throttleMs);
    }

    private String formatMessage(String level, String color, String message) {
        return String.format("%s[%s]%s %s[%s]%s %s",
                color, level, RESET,
                DIM, className, RESET,
                message
        );
    }

    public boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }

    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }

    public boolean isErrorEnabled() {
        return logger.isErrorEnabled();
    }

    private enum LogLevel {
        TRACE,
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    private static final class RateLimitState {
        private long lastEmit = 0L;
        private int suppressed = 0;

        synchronized RateLimitResult tryAcquire(long now, long throttleMs) {
            if (now - lastEmit >= throttleMs) {
                int suppressedSinceLast = suppressed;
                suppressed = 0;
                lastEmit = now;
                return new RateLimitResult(true, suppressedSinceLast);
            }
            suppressed++;
            return RateLimitResult.DROPPED;
        }
    }

    private record RateLimitResult(boolean shouldLog, int suppressedCount) {
        private static final RateLimitResult DROPPED = new RateLimitResult(false, 0);
    }
}
