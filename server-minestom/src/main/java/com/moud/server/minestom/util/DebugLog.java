package com.moud.server.minestom.util;

import java.time.Instant;

public final class DebugLog {
    private static final boolean DEBUG = parseBool(System.getenv("MOUD_DEBUG"))
            || parseBool(System.getProperty("moud.debug"));

    private DebugLog() {
    }

    public static boolean enabled() {
        return DEBUG;
    }

    public static void info(String subsystem, String message) {
        System.out.println(prefix("INFO", subsystem) + safe(message));
    }

    public static void debug(String subsystem, String message) {
        if (!DEBUG) {
            return;
        }
        System.out.println(prefix("DEBUG", subsystem) + safe(message));
    }

    public static void warn(String subsystem, String message) {
        System.err.println(prefix("WARN", subsystem) + safe(message));
    }

    public static void error(String subsystem, String message) {
        System.err.println(prefix("ERROR", subsystem) + safe(message));
    }

    public static void error(String subsystem, String message, Throwable t) {
        System.err.println(prefix("ERROR", subsystem) + safe(message));
        if (t != null) {
            t.printStackTrace(System.err);
        }
    }

    private static String prefix(String level, String subsystem) {
        String sub = (subsystem == null || subsystem.isBlank()) ? "moud" : subsystem;
        return "[" + Instant.now() + "][" + level + "][" + sub + "] ";
    }

    private static String safe(String message) {
        return message == null ? "" : message;
    }

    private static boolean parseBool(String v) {
        if (v == null) {
            return false;
        }
        String s = v.trim().toLowerCase();
        return "1".equals(s) || "true".equals(s) || "yes".equals(s) || "y".equals(s) || "on".equals(s);
    }
}

