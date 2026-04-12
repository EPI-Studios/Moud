package com.moud.client.fabric.util;

import com.moud.client.fabric.editor.diagnostics.EditorDiagnostics;
import com.moud.core.util.ParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClientDebugLog {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoudClient");
    private static final boolean DEBUG = ParseUtils.parseBool(System.getenv("MOUD_DEBUG_CLIENT"))
            || ParseUtils.parseBool(System.getenv("MOUD_DEBUG"))
            || ParseUtils.parseBool(System.getProperty("moud.client.debug"))
            || ParseUtils.parseBool(System.getProperty("moud.debug"));

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
        info("Client", message);
    }

    public static void warn(String message) {
        warn("Client", message);
    }

    public static void error(String message) {
        error("Client", message);
    }

    public static void error(String message, Throwable t) {
        error("Client", message, t);
    }

    public static void info(String source, String message) {
        EditorDiagnostics.info(source, safe(message));
        LOGGER.info(safe(message));
    }

    public static void warn(String source, String message) {
        EditorDiagnostics.warn(source, safe(message));
        LOGGER.warn(safe(message));
    }

    public static void error(String source, String message) {
        EditorDiagnostics.error(source, safe(message));
        LOGGER.error(safe(message));
    }

    public static void error(String source, String message, Throwable t) {
        EditorDiagnostics.error(source, t == null ? safe(message) : safe(message) + " (" + t.getClass().getSimpleName() + ": " + safe(t.getMessage()) + ")");
        LOGGER.error(safe(message), t);
    }

    private static String safe(String message) {
        return message == null ? "" : message;
    }

}
