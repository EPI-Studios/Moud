package com.moud.server.minestom.util;

import com.moud.core.util.ParseUtils;
import java.io.PrintStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class DebugLog {
    private static final boolean DEBUG = ParseUtils.parseBool(System.getenv("MOUD_DEBUG"))
            || ParseUtils.parseBool(System.getProperty("moud.debug"));
    private static final boolean COLOR = detectColor();

    private static final String RESET  = "\u001B[0m";
    private static final String BOLD   = "\u001B[1m";
    private static final String DIM    = "\u001B[2m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED    = "\u001B[31m";
    private static final String CYAN   = "\u001B[36m";

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private DebugLog() {}

    public static boolean enabled() {
        return DEBUG;
    }

    public static void info(String subsystem, String message) {
        print(System.out, "INFO ", GREEN, subsystem, message);
    }

    public static void debug(String subsystem, String message) {
        if (!DEBUG) return;
        print(System.out, "DEBUG", CYAN, subsystem, message);
    }

    public static void warn(String subsystem, String message) {
        print(System.err, "WARN ", YELLOW, subsystem, message);
    }

    public static void error(String subsystem, String message) {
        print(System.err, "ERROR", RED, subsystem, message);
    }

    public static void error(String subsystem, String message, Throwable t) {
        print(System.err, "ERROR", RED, subsystem, message);
        if (t != null && DEBUG) t.printStackTrace(System.err);
    }

    private static void print(PrintStream out, String level, String levelColor, String subsystem, String message) {
        String time       = LocalTime.now().format(TIME_FMT);
        String sub        = pad((subsystem == null || subsystem.isBlank() ? "MOUD" : subsystem).toUpperCase(), 12);
        String msg        = message == null ? "" : message;
        if (COLOR) {
            out.println(
                DIM + time + RESET + "  " +
                BOLD + levelColor + level + RESET + "  " +
                DIM + sub + "·  " + RESET +
                msg
            );
        } else {
            out.println(time + "  " + level + "  " + sub + "·  " + msg);
        }
    }

    private static String pad(String s, int width) {
        if (s.length() >= width) return s + "  ";
        return s + " ".repeat(width - s.length());
    }

    private static boolean detectColor() {
        String noColor = System.getenv("NO_COLOR");
        if (noColor != null && !noColor.isEmpty()) return false;
        if ("dumb".equals(System.getenv("TERM"))) return false;
        return true;
    }

}
