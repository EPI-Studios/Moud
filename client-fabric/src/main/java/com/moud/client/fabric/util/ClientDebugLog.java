package com.moud.client.fabric.util;

public final class ClientDebugLog {
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
        System.out.println("[moud-client][debug] " + safe(message));
    }

    public static void info(String message) {
        System.out.println("[moud-client][info] " + safe(message));
    }

    public static void warn(String message) {
        System.err.println("[moud-client][warn] " + safe(message));
    }

    public static void error(String message) {
        System.err.println("[moud-client][error] " + safe(message));
    }

    public static void error(String message, Throwable t) {
        System.err.println("[moud-client][error] " + safe(message));
        if (t != null) {
            t.printStackTrace(System.err);
        }
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

