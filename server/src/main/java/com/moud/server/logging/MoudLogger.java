package com.moud.server.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

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

    private static final Marker SUCCESS_MARKER = MarkerFactory.getMarker("SUCCESS");
    private static final Marker SCRIPT_ERROR_MARKER = MarkerFactory.getMarker("SCRIPT_ERROR");
    private static final Marker CRITICAL_MARKER = MarkerFactory.getMarker("CRITICAL");
    private static final Marker API_MARKER = MarkerFactory.getMarker("API");
    private static final Marker NETWORK_MARKER = MarkerFactory.getMarker("NETWORK");

    private final Logger logger;
    private final String className;

    private MoudLogger(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
        this.className = clazz.getSimpleName();
    }

    public static MoudLogger getLogger(Class<?> clazz) {
        return new MoudLogger(clazz);
    }

    public void info(String message, Object... args) {
        logger.info(formatMessage("INFO", BLUE, message), args);
    }

    public void debug(String message, Object... args) {
        logger.debug(formatMessage("DEBUG", DIM + WHITE, message), args);
    }

    public void warn(String message, Object... args) {
        logger.warn(formatMessage("WARN", YELLOW, message), args);
    }

    public void error(String message, Object... args) {
        logger.error(formatMessage("ERROR", RED, message), args);
    }

    public void error(String message, Throwable throwable, Object... args) {
        logger.error(formatMessage("ERROR", RED, message), args, throwable);
    }

    public void success(String message, Object... args) {
        logger.info(SUCCESS_MARKER, formatMessage("SUCCESS", BRIGHT_GREEN, message), args);
    }

    public void critical(String message, Object... args) {
        logger.error(CRITICAL_MARKER, formatMessage("CRITICAL", BOLD + BRIGHT_RED, message), args);
    }

    public void scriptError(String message, Object... args) {
        logger.error(SCRIPT_ERROR_MARKER, formatMessage("SCRIPT", BRIGHT_MAGENTA, message), args);
    }

    public void api(String message, Object... args) {
        logger.info(API_MARKER, formatMessage("API", CYAN, message), args);
    }

    public void network(String message, Object... args) {
        logger.debug(NETWORK_MARKER, formatMessage("NET", BRIGHT_CYAN, message), args);
    }

    public void startup(String message, Object... args) {
        logger.info(formatMessage("STARTUP", BOLD + BRIGHT_GREEN, message), args);
    }

    public void shutdown(String message, Object... args) {
        logger.info(formatMessage("SHUTDOWN", BOLD + YELLOW, message), args);
    }

    private String formatMessage(String level, String color, String message) {
        return String.format("%s[%s]%s %s[%s]%s %s",
                color, level, RESET,
                DIM, className, RESET,
                message
        );
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
}