package com.moud.server;

import com.moud.server.logging.MoudLogger;

public class ConsoleAPI {
    private static final MoudLogger LOGGER = MoudLogger.getLogger("Script.Console".getClass());
    private static final int MAX_LOG_LENGTH = 4096;

    public void log(Object... args) {
        String message = formatArgs(args);
        if (message.length() > MAX_LOG_LENGTH) {
            message = message.substring(0, MAX_LOG_LENGTH) + "... (truncated)";
        }
        LOGGER.info(message);
    }

    public void warn(Object... args) {
        String message = formatArgs(args);
        if (message.length() > MAX_LOG_LENGTH) {
            message = message.substring(0, MAX_LOG_LENGTH) + "... (truncated)";
        }
        LOGGER.warn(message);
    }

    public void error(Object... args) {
        String message = formatArgs(args);
        if (message.length() > MAX_LOG_LENGTH) {
            message = message.substring(0, MAX_LOG_LENGTH) + "... (truncated)";
        }
        LOGGER.error(message);
    }

    public void debug(Object... args) {
        String message = formatArgs(args);
        if (message.length() > MAX_LOG_LENGTH) {
            message = message.substring(0, MAX_LOG_LENGTH) + "... (truncated)";
        }
        LOGGER.debug(message);
    }

    public void success(Object... args) {
        String message = formatArgs(args);
        if (message.length() > MAX_LOG_LENGTH) {
            message = message.substring(0, MAX_LOG_LENGTH) + "... (truncated)";
        }
        LOGGER.success(message);
    }

    public void trace(String message, Object... context) {
        if (context.length > 0) {
            LOGGER.debug("TRACE: {} | Context: {}", message, formatArgs(context));
        } else {
            LOGGER.debug("TRACE: {}", message);
        }
    }

    public void assert_(boolean condition, String message) {
        if (!condition) {
            LOGGER.error("ASSERTION FAILED: {}", message);
            throw new AssertionError(message);
        }
    }

    public void time(String label) {
        long timestamp = System.currentTimeMillis();
        LOGGER.debug("TIMER START: {} at {}", label, timestamp);
    }

    public void timeEnd(String label) {
        long timestamp = System.currentTimeMillis();
        LOGGER.debug("TIMER END: {} at {}", label, timestamp);
    }

    public void group(String label) {
        LOGGER.info("┌─ GROUP: {}", label);
    }

    public void groupEnd() {
        LOGGER.info("└─ GROUP END");
    }

    public void table(Object data) {
        LOGGER.info("TABLE: {}", formatTableData(data));
    }

    private String formatArgs(Object[] args) {
        if (args.length == 0) return "";
        if (args.length == 1) return formatSingleArg(args[0]);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(" ");
            sb.append(formatSingleArg(args[i]));
        }
        return sb.toString();
    }

    private String formatSingleArg(Object arg) {
        if (arg == null) return "null";
        if (arg instanceof String) return (String) arg;
        if (arg instanceof Number) return arg.toString();
        if (arg instanceof Boolean) return arg.toString();

        try {
            return arg.toString();
        } catch (Exception e) {
            return "[Object: " + arg.getClass().getSimpleName() + "]";
        }
    }

    private String formatTableData(Object data) {
        if (data == null) return "null";
        return data.toString();
    }
}