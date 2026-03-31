package com.moud.client.fabric.util;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClientDebugLog {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoudClient");
    private static final boolean DEBUG = parseBool(System.getenv("MOUD_DEBUG_CLIENT"))
            || parseBool(System.getenv("MOUD_DEBUG"))
            || parseBool(System.getProperty("moud.client.debug"))
            || parseBool(System.getProperty("moud.debug"));

    private ClientDebugLog() {
    }

    public static boolean enabled() {
        return DEBUG;
    }

    public static void debug(String message) {
        if (!DEBUG) {
            return;
        }
        LOGGER.debug(safe(message));
    }

    public static void info(String message) {
        LOGGER.info(safe(message));
    }

    public static void warn(String message) {
        LOGGER.warn(safe(message));
    }

    public static void error(String message) {
        LOGGER.error(safe(message));
    }

    public static void error(String message, Throwable t) {
        LOGGER.error(safe(message), t);
    }

    private static String safe(String message) {
        return message == null ? "" : message;
    }

    private static boolean parseBool(String v) {
        if (v == null) {
            return false;
        }
        String s = v.trim().toLowerCase(Locale.ROOT);
        return "1".equals(s) || "true".equals(s) || "yes".equals(s) || "y".equals(s) || "on".equals(s);
    }
}
